package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualVinculo;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Núcleo de tesorería (caja mayor / caja virtual, cash-only). Es el **único** punto
 * que toca el saldo de una caja virtual: mantiene el saldo por {@code (caja, moneda)}
 * en {@code caja_virtual_saldo} con lock pesimista, aplica movimientos con signo,
 * anula con contra-movimiento (ledger inmutable) y bloquea la anulación cross-módulo.
 *
 * <p>Compatibilidad: sincroniza el shim {@code saldo_gs/rs/ds} de {@code CajaVirtual}
 * para no romper RRHH ni la UI actual hasta que se porten (F2/F8).
 *
 * <p>Diseñado genérico para que la contraparte bancaria (F4) reutilice el patrón.
 */
@Service
@AllArgsConstructor
public class TesoreriaService {

    private final CajaVirtualSaldoRepository saldoRepository;

    private final TesoreriaSecurityService seguridad;
    private final CajaVirtualRepository cajaVirtualRepository;
    private final MonedaRepository monedaRepository;
    private final MovimientoCajaVirtualRepository movimientoRepository;
    private final com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository;

    /** Los tipos que restan del saldo (además de AJUSTE, que ya llega firmado). */
    static boolean esEgreso(CajaVirtualTipoMovimiento tipo) {
        return tipo == CajaVirtualTipoMovimiento.EGRESO
                || tipo == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA
                || tipo == CajaVirtualTipoMovimiento.PAGO_PROVEEDOR;
    }

    /**
     * Delta firmado a aplicar al saldo según el tipo. AJUSTE conserva el signo de la cantidad.
     * Package-private: el reporte de movimientos totaliza con la misma regla.
     */
    static BigDecimal signedDelta(CajaVirtualTipoMovimiento tipo, BigDecimal cantidad) {
        if (tipo == CajaVirtualTipoMovimiento.AJUSTE) return cantidad;
        return esEgreso(tipo) ? cantidad.abs().negate() : cantidad.abs();
    }

    private Moneda resolverMoneda(Moneda moneda) {
        if (moneda != null) return moneda;
        Moneda gs = monedaRepository.findFirstByDenominacionContainingIgnoreCaseOrderByIdAsc("GUARAN");
        if (gs == null) throw new GraphQLException("No hay moneda Guaraní configurada para la caja virtual");
        return gs;
    }

    /**
     * Registra un movimiento y aplica su efecto al saldo de la caja, de forma atómica.
     * La cantidad llega positiva para ingreso/egreso; AJUSTE puede llegar firmada.
     */
    @Transactional
    public MovimientoCajaVirtual registrar(MovimientoCajaVirtual mov) {
        // Choke point del ACL de escritura: TODA la plata que entra o sale de una caja pasa
        // por aca (RRHH, CPP, gastos, maletin, retiros, entradas varias, devoluciones).
        // Los procesos de sistema (schedulers, replicacion) no tienen sesion y pasan.
        seguridad.requireEscrituraCaja(mov.getCajaVirtual() != null ? mov.getCajaVirtual().getId() : null);
        if (mov.getActivo() == null) mov.setActivo(true);
        Moneda moneda = resolverMoneda(mov.getMoneda());
        mov.setMoneda(moneda);
        CajaVirtual caja = cajaVirtualRepository.findById(mov.getCajaVirtual().getId())
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada"));

        BigDecimal cantidad = mov.getCantidad() != null ? BigDecimal.valueOf(mov.getCantidad()) : BigDecimal.ZERO;
        BigDecimal delta = signedDelta(mov.getTipoMovimiento(), cantidad);
        BigDecimal anterior = aplicarSaldo(caja, moneda, delta);

        mov.setSaldoAnterior(anterior.doubleValue());
        mov.setSaldoPosterior(anterior.add(delta).doubleValue());
        return movimientoRepository.save(mov);
    }

    /**
     * Aplica un delta firmado al saldo de {@code (caja, moneda)} con lock pesimista.
     * Rechaza el descubierto si la caja no lo permite (CN2). Sincroniza el shim.
     * Devuelve el saldo anterior.
     */
    private BigDecimal aplicarSaldo(CajaVirtual caja, Moneda moneda, BigDecimal delta) {
        // Asegura la fila (upsert idempotente) antes de tomar el lock: evita la carrera del
        // primer movimiento donde dos transacciones no encuentran fila y ambas insertan.
        saldoRepository.ensureRow(caja.getId(), moneda.getId());
        CajaVirtualSaldo saldo = saldoRepository.lockByCajaVirtualIdAndMonedaId(caja.getId(), moneda.getId())
                .orElseThrow(() -> new GraphQLException("No se pudo tomar el saldo de la caja"));
        BigDecimal anterior = saldo.getSaldo() != null ? saldo.getSaldo() : BigDecimal.ZERO;
        BigDecimal nuevo = anterior.add(delta);
        boolean permiteNegativo = Boolean.TRUE.equals(caja.getPermiteSaldoNegativo());
        if (!permiteNegativo && nuevo.compareTo(BigDecimal.ZERO) < 0) {
            throw new GraphQLException("Saldo insuficiente en la caja virtual");
        }
        saldo.setSaldo(nuevo);
        saldoRepository.save(saldo);
        sincronizarShim(caja, moneda, nuevo);
        return anterior;
    }

    /** Refleja el saldo nuevo en las columnas shim saldo_gs/rs/ds (solo Gs/Rs/Ds; otras monedas viven solo en la tabla). */
    private void sincronizarShim(CajaVirtual caja, Moneda moneda, BigDecimal nuevo) {
        String den = moneda.getDenominacion() != null ? moneda.getDenominacion().toUpperCase() : "";
        double val = nuevo.doubleValue();
        boolean tocado = false;
        if (den.contains("GUARAN")) { caja.setSaldoGs(val); tocado = true; }
        else if (den.contains("REAL")) { caja.setSaldoRs(val); tocado = true; }
        else if (den.contains("DOLAR") || den.contains("DÓLAR")) { caja.setSaldoDs(val); tocado = true; }
        if (tocado) cajaVirtualRepository.save(caja);
    }

    /**
     * Transferencia entre dos cajas (misma moneda): egreso en origen + ingreso en destino.
     * Se procesan en orden canónico de id de caja ascendente para evitar deadlock
     * entre transferencias concurrentes A→B y B→A.
     */
    @Transactional
    public Boolean transferir(Long origenId, Long destinoId, Double cantidad,
                              Moneda moneda, String descripcion, Usuario usuario) {
        if (origenId.equals(destinoId)) throw new GraphQLException("La caja origen y destino no pueden ser la misma");
        // Mover plata entre cajas exige permiso en las dos: es un egreso y un ingreso.
        seguridad.requireEscrituraCaja(origenId);
        seguridad.requireEscrituraCaja(destinoId);
        CajaVirtual origen = cajaVirtualRepository.findById(origenId)
                .orElseThrow(() -> new GraphQLException("Caja origen no encontrada"));
        CajaVirtual destino = cajaVirtualRepository.findById(destinoId)
                .orElseThrow(() -> new GraphQLException("Caja destino no encontrada"));

        MovimientoCajaVirtual salida = build(origen, origen, destino, CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA,
                cantidad, moneda, descripcion, usuario);
        MovimientoCajaVirtual entrada = build(destino, origen, destino, CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA,
                cantidad, moneda, descripcion, usuario);

        // Orden canónico por id de caja (lock del menor primero) — evita deadlock.
        if (origenId <= destinoId) { salida = registrar(salida); entrada = registrar(entrada); }
        else { entrada = registrar(entrada); salida = registrar(salida); }
        // Cada pata apunta a la otra: es lo que permite anular la transferencia entera y no una mitad
        // (issue #376). Mutuo y no de un solo sentido porque así la otra pata se busca por clave.
        salida.setReferenciaId(entrada.getId());
        entrada.setReferenciaId(salida.getId());
        movimientoRepository.save(salida);
        movimientoRepository.save(entrada);
        return true;
    }

    private MovimientoCajaVirtual build(CajaVirtual caja, CajaVirtual origen, CajaVirtual destino,
                                        CajaVirtualTipoMovimiento tipo, Double cantidad, Moneda moneda,
                                        String descripcion, Usuario usuario) {
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setCajaVirtual(caja);
        m.setTipoMovimiento(tipo);
        m.setCantidad(cantidad);
        m.setMoneda(moneda);
        m.setCajaOrigen(origen);
        m.setCajaDestino(destino);
        m.setDescripcion(descripcion);
        m.setUsuario(usuario);
        m.setOrigenTipo(OrigenMovimientoTipo.MANUAL);
        m.setActivo(true);
        return m;
    }

    /**
     * Anula un movimiento generando un contra-movimiento AJUSTE que revierte su efecto.
     * Nunca edita/borra el original (ledger inmutable). Bloquea la anulación si el
     * movimiento proviene de otro módulo (debe anularse desde su dominio dueño).
     */
    /**
     * Anulación desde la caja mayor (operación manual). Solo permite anular
     * movimientos de origen MANUAL; los que provienen de otro módulo se bloquean
     * y deben anularse desde su dominio dueño (que llama a {@link #revertir}).
     */
    @Transactional
    public MovimientoCajaVirtual anular(Long movimientoId, String motivo, Usuario usuario) {
        // Una pata de transferencia no se anula sola: devolver la plata a una caja sin sacarla de la otra
        // la duplica (o la hace desaparecer). Se mira por proyección, sin cargar la entidad.
        MovimientoCajaVirtualVinculo datos = movimientoRepository.findVinculoById(movimientoId).orElse(null);
        if (datos != null && datos.esPataDeTransferencia()) {
            return anularTransferencia(datos, motivo, usuario);
        }
        // Con lock: sin él, dos anulaciones del mismo movimiento (dos usuarios, o un reintento tras
        // una respuesta perdida) pasaban las dos y posteaban dos contra-movimientos.
        MovimientoCajaVirtual orig = movimientoRepository.lockById(movimientoId)
                .orElseThrow(() -> new GraphQLException("Movimiento no encontrado: " + movimientoId));
        // Anular mueve plata (contra-movimiento): mismo permiso que registrarla.
        seguridad.requireEscrituraCaja(orig.getCajaVirtual() != null ? orig.getCajaVirtual().getId() : null);

        // revertir() no mira el estado del original (los módulos dueños se cuidan solos): acá es
        // donde se corta la segunda anulación. Un activo nulo cuenta como activo, igual que en registrar().
        if (Boolean.FALSE.equals(orig.getActivo())) {
            throw new GraphQLException("El movimiento #" + orig.getId() + " ya está anulado.");
        }

        OrigenMovimientoTipo origen = orig.getOrigenTipo();
        if (origen == OrigenMovimientoTipo.ANULACION) {
            throw new GraphQLException("No se puede anular un contra-movimiento de anulación.");
        }
        // MALETIN se opera manualmente en tesorería (no tiene módulo dueño con reversa propia),
        // así que es anulable directo desde la caja mayor igual que MANUAL.
        if (origen != null && origen != OrigenMovimientoTipo.MANUAL && origen != OrigenMovimientoTipo.MALETIN) {
            throw new GraphQLException("Este movimiento proviene de " + origen
                    + "; anúlelo desde su módulo de origen, no desde la caja mayor.");
        }
        // CN4: límite de anulación por antigüedad (null = sin límite).
        Integer diasLimite = diasLimiteAnulacion();
        if (diasLimite != null && diasLimite > 0 && orig.getCreadoEn() != null
                && orig.getCreadoEn().isBefore(java.time.LocalDateTime.now().minusDays(diasLimite))) {
            throw new GraphQLException("El movimiento supera el límite de " + diasLimite
                    + " días para anular. Requiere autorización.");
        }
        return revertir(orig, motivo, usuario);
    }

    private static final String SIN_CONTRAPARTE = "No se pudo identificar la otra pata de esta transferencia:"
            + " no se anula a medias. Avise a soporte.";

    /**
     * Anula una transferencia entre cajas <b>completa</b> a partir de cualquiera de sus dos patas.
     *
     * <p>Orden: permiso sobre las dos cajas → ubicar la otra pata por su vínculo → lock de los dos
     * movimientos por id ascendente (dos anulaciones que entran cada una por una pata se cruzarían si
     * cada una tomara «la suya» primero) → estado leído de la base → reversas por caja ascendente, el
     * orden en que {@link #transferir} toma los saldos.</p>
     *
     * <p>Si la otra pata ya estaba anulada —alguien anuló antes esa mitad— se anula solo la pedida: es lo
     * que deja la transferencia consistente. Sin un vínculo válido no se adivina: se rechaza.</p>
     */
    private MovimientoCajaVirtual anularTransferencia(MovimientoCajaVirtualVinculo pedida, String motivo, Usuario usuario) {
        if (pedida.getCajaOrigenId() == null || pedida.getCajaDestinoId() == null) {
            throw new GraphQLException(SIN_CONTRAPARTE);
        }
        // Antes de buscar nada y de tomar ningún lock: quien no puede mover plata en las dos cajas no tiene
        // por qué enterarse del estado de la transferencia. Mensaje propio: el genérico haría pensar que
        // falta el permiso sobre la caja que se está mirando.
        try {
            seguridad.requireEscrituraCaja(pedida.getCajaOrigenId());
            seguridad.requireEscrituraCaja(pedida.getCajaDestinoId());
        } catch (GraphQLException e) {
            throw new GraphQLException("Para anular una transferencia hace falta permiso de escritura en las dos cajas.");
        }

        MovimientoCajaVirtualVinculo otra = pedida.getReferenciaId() != null
                ? movimientoRepository.findVinculoById(pedida.getReferenciaId()).orElse(null) : null;
        if (!pedida.esContraparteDe(otra)) {
            throw new GraphQLException(SIN_CONTRAPARTE);
        }

        Long menor = Math.min(pedida.getId(), otra.getId());
        Long mayor = Math.max(pedida.getId(), otra.getId());
        MovimientoCajaVirtual primero = movimientoRepository.lockById(menor)
                .orElseThrow(() -> new GraphQLException("Movimiento no encontrado: " + menor));
        MovimientoCajaVirtual segundo = movimientoRepository.lockById(mayor)
                .orElseThrow(() -> new GraphQLException("Movimiento no encontrado: " + mayor));
        MovimientoCajaVirtual movPedido = menor.equals(pedida.getId()) ? primero : segundo;
        MovimientoCajaVirtual movOtra = movPedido == primero ? segundo : primero;

        // El estado, de la base y después del lock (regla del módulo: lockById no refresca lo ya cargado).
        if (!movimientoRepository.findActivoById(pedida.getId()).orElse(!Boolean.FALSE.equals(movPedido.getActivo()))) {
            throw new GraphQLException("El movimiento #" + pedida.getId() + " ya está anulado.");
        }
        boolean otraActiva = movimientoRepository.findActivoById(otra.getId())
                .orElse(!Boolean.FALSE.equals(movOtra.getActivo()));
        if (!otraActiva && !movimientoRepository.existsByOrigenTipoAndOrigenId(OrigenMovimientoTipo.ANULACION, otra.getId())) {
            // Inactiva pero sin contra-movimiento: su efecto sigue en el saldo. Anular solo esta descuadraría.
            throw new GraphQLException(SIN_CONTRAPARTE);
        }

        // CN4 sobre la pata más vieja: que no dependa de por cuál de las dos se entra.
        Integer diasLimite = diasLimiteAnulacion();
        java.time.LocalDateTime creada = masVieja(pedida.getCreadoEn(), otra.getCreadoEn());
        if (diasLimite != null && diasLimite > 0 && creada != null
                && creada.isBefore(java.time.LocalDateTime.now().minusDays(diasLimite))) {
            throw new GraphQLException("El movimiento supera el límite de " + diasLimite
                    + " días para anular. Requiere autorización.");
        }

        String razon = motivo != null ? motivo : "";
        if (!otraActiva) {
            return revertirPata(movPedido, razon, usuario);
        }
        // La pata que nadie pidió anular dice por qué aparece su contra-movimiento en la otra caja.
        String razonOtra = (razon.isEmpty() ? "" : razon + " — ") + "TRANSFERENCIA ANULADA JUNTO CON EL MOV #" + pedida.getId();
        boolean pedidaPrimero = movPedido.getCajaVirtual().getId() <= movOtra.getCajaVirtual().getId();
        MovimientoCajaVirtual contraPedida;
        if (pedidaPrimero) {
            contraPedida = revertirPata(movPedido, razon, usuario);
            revertirPata(movOtra, razonOtra, usuario);
        } else {
            revertirPata(movOtra, razonOtra, usuario);
            contraPedida = revertirPata(movPedido, razon, usuario);
        }
        return contraPedida;
    }

    /**
     * Revierte una pata. Devolver la entrada saca plata de la caja destino: si esa plata ya se gastó, el
     * rechazo dice cuál caja y por qué, en vez del «Saldo insuficiente» pelado. La transacción se deshace entera.
     */
    private MovimientoCajaVirtual revertirPata(MovimientoCajaVirtual pata, String motivo, Usuario usuario) {
        try {
            return revertir(pata, motivo, usuario);
        } catch (GraphQLException e) {
            if (pata.getTipoMovimiento() == CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA
                    && e.getMessage() != null && e.getMessage().startsWith("Saldo insuficiente")) {
                String caja = pata.getCajaVirtual() != null && pata.getCajaVirtual().getNombre() != null
                        ? pata.getCajaVirtual().getNombre() : "destino";
                throw new GraphQLException("No se puede anular la transferencia: la caja " + caja
                        + " ya no tiene saldo suficiente para devolver lo transferido.");
            }
            throw e;
        }
    }

    private static java.time.LocalDateTime masVieja(java.time.LocalDateTime a, java.time.LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /** Días límite de anulación configurados (CN4), o null si no hay config/límite. */
    private Integer diasLimiteAnulacion() {
        try {
            return configRepository.findAll().stream().findFirst()
                    .map(com.franco.dev.domain.empresarial.ConfiguracionGeneral::getDiasLimiteAnulacion)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Busca un movimiento para revertirlo. Lo usan los servicios dueños al anular su operación. */
    public MovimientoCajaVirtual findMovimiento(Long id) {
        return movimientoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Movimiento no encontrado: " + id));
    }

    /**
     * Genera el contra-movimiento que revierte el efecto de {@code orig} (ledger
     * inmutable, nunca se edita/borra el original). Sin el guard cross-módulo: lo
     * invoca el módulo dueño de la operación al anularla.
     *
     * <p>Un movimiento se revierte <b>una sola vez</b> (issue #376): se toma con lock y se rechaza si ya
     * está inactivo. El control vive acá y no en cada módulo dueño porque acá es donde se postea el
     * contra-movimiento: dos anulaciones simultáneas del mismo vale, entrada o pago leían las dos el
     * documento sin anular y cada una devolvía la plata.</p>
     */
    @Transactional
    public MovimientoCajaVirtual revertir(MovimientoCajaVirtual orig, String motivo, Usuario usuario) {
        if (orig.getId() == null) {
            throw new GraphQLException("No se puede revertir un movimiento que no está registrado.");
        }
        // El permiso sobre la caja va antes de mirar el estado, igual que en anular(): quien no puede mover
        // plata en esta caja no tiene por qué enterarse de si el movimiento ya está anulado, ni esperar su
        // lock. registrar() lo vuelve a exigir al postear el contra-movimiento.
        seguridad.requireEscrituraCaja(orig.getCajaVirtual() != null ? orig.getCajaVirtual().getId() : null);
        // El lock serializa; el estado se lee después y de la base. lockById devuelve la instancia que el
        // llamador ya tuviera cargada (casi siempre: la buscó para pasarla acá), con el activo de antes de
        // esperar. Sin fila en la proyección vale el de la entidad; un activo nulo cuenta como activo.
        movimientoRepository.lockById(orig.getId());
        boolean activo = movimientoRepository.findActivoById(orig.getId())
                .orElse(!Boolean.FALSE.equals(orig.getActivo()));
        if (!activo) {
            throw new GraphQLException("El movimiento #" + orig.getId() + " ya está anulado.");
        }
        // Recomputa el efecto con BigDecimal (no restando los snapshots Double, que arrastran
        // error de punto flotante en monedas con decimales). El contra-movimiento es el negado.
        BigDecimal cantidadOrig = orig.getCantidad() != null ? BigDecimal.valueOf(orig.getCantidad()) : BigDecimal.ZERO;
        BigDecimal efecto = signedDelta(orig.getTipoMovimiento(), cantidadOrig);

        MovimientoCajaVirtual contra = new MovimientoCajaVirtual();
        contra.setCajaVirtual(orig.getCajaVirtual());
        contra.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
        contra.setCantidad(efecto.negate().doubleValue()); // AJUSTE firmado: revierte el efecto original
        contra.setMoneda(orig.getMoneda());
        contra.setUsuario(usuario);
        contra.setReferenciaId(orig.getId());
        contra.setOrigenTipo(OrigenMovimientoTipo.ANULACION);
        contra.setOrigenId(orig.getId());
        contra.setDescripcion("ANULACION: " + (motivo != null ? motivo : "") + " (mov #" + orig.getId() + ")");
        contra.setActivo(true);
        MovimientoCajaVirtual posteado = registrar(contra);
        // Marca el original como inactivo (consistente con banco, que marca anulado) → la UI lo
        // tacha y el filtro "solo activos" lo oculta. No afecta el saldo (ya lo revirtió el contra).
        orig.setActivo(false);
        movimientoRepository.save(orig);
        return posteado;
    }

    /**
     * Red de seguridad: reconstruye el saldo de una caja por moneda sumando los deltas
     * de sus movimientos activos. Debe coincidir con el saldo persistido si todo está sano.
     */
    @Transactional
    public void recalcularSaldos(Long cajaId) {
        CajaVirtual caja = cajaVirtualRepository.findById(cajaId)
                .orElseThrow(() -> new GraphQLException("Caja virtual no encontrada: " + cajaId));
        List<MovimientoCajaVirtual> movs = movimientoRepository.findByCajaVirtualIdAndActivoTrue(cajaId);

        Map<Long, BigDecimal> totalPorMoneda = new HashMap<>();
        Map<Long, Moneda> monedas = new HashMap<>();
        for (MovimientoCajaVirtual m : movs) {
            Moneda moneda = resolverMoneda(m.getMoneda());
            BigDecimal cantidad = m.getCantidad() != null ? BigDecimal.valueOf(m.getCantidad()) : BigDecimal.ZERO;
            BigDecimal delta = signedDelta(m.getTipoMovimiento(), cantidad);
            totalPorMoneda.merge(moneda.getId(), delta, BigDecimal::add);
            monedas.putIfAbsent(moneda.getId(), moneda);
        }
        for (Map.Entry<Long, BigDecimal> e : totalPorMoneda.entrySet()) {
            Moneda moneda = monedas.get(e.getKey());
            saldoRepository.ensureRow(cajaId, e.getKey());
            CajaVirtualSaldo saldo = saldoRepository.lockByCajaVirtualIdAndMonedaId(cajaId, e.getKey())
                    .orElseThrow(() -> new GraphQLException("No se pudo tomar el saldo para recalcular"));
            saldo.setSaldo(e.getValue());
            saldoRepository.save(saldo);
            sincronizarShim(caja, moneda, e.getValue());
        }
    }
}
