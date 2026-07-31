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
    private final CajaVirtualRepository cajaVirtualRepository;
    private final MonedaRepository monedaRepository;
    private final MovimientoCajaVirtualRepository movimientoRepository;
    private final com.franco.dev.repository.empresarial.ConfiguracionGeneralRepository configRepository;

    /** Los tipos que restan del saldo (además de AJUSTE, que ya llega firmado). */
    private static boolean esEgreso(CajaVirtualTipoMovimiento tipo) {
        return tipo == CajaVirtualTipoMovimiento.EGRESO
                || tipo == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA
                || tipo == CajaVirtualTipoMovimiento.PAGO_PROVEEDOR;
    }

    /** Delta firmado a aplicar al saldo según el tipo. AJUSTE conserva el signo de la cantidad. */
    private static BigDecimal signedDelta(CajaVirtualTipoMovimiento tipo, BigDecimal cantidad) {
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
        CajaVirtualSaldo saldo = saldoRepository.lockByCajaVirtualIdAndMonedaId(caja.getId(), moneda.getId())
                .orElseGet(() -> {
                    CajaVirtualSaldo s = new CajaVirtualSaldo();
                    s.setCajaVirtual(caja);
                    s.setMoneda(moneda);
                    s.setSaldo(BigDecimal.ZERO);
                    return saldoRepository.save(s);
                });
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
        CajaVirtual origen = cajaVirtualRepository.findById(origenId)
                .orElseThrow(() -> new GraphQLException("Caja origen no encontrada"));
        CajaVirtual destino = cajaVirtualRepository.findById(destinoId)
                .orElseThrow(() -> new GraphQLException("Caja destino no encontrada"));

        MovimientoCajaVirtual salida = build(origen, origen, destino, CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA,
                cantidad, moneda, descripcion, usuario);
        MovimientoCajaVirtual entrada = build(destino, origen, destino, CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA,
                cantidad, moneda, descripcion, usuario);

        // Orden canónico por id de caja (lock del menor primero) — evita deadlock.
        if (origenId <= destinoId) { registrar(salida); registrar(entrada); }
        else { registrar(entrada); registrar(salida); }
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
        MovimientoCajaVirtual orig = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new GraphQLException("Movimiento no encontrado: " + movimientoId));

        OrigenMovimientoTipo origen = orig.getOrigenTipo();
        if (origen == OrigenMovimientoTipo.ANULACION) {
            throw new GraphQLException("No se puede anular un contra-movimiento de anulación.");
        }
        if (origen != null && origen != OrigenMovimientoTipo.MANUAL) {
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
     */
    @Transactional
    public MovimientoCajaVirtual revertir(MovimientoCajaVirtual orig, String motivo, Usuario usuario) {
        double efecto = (orig.getSaldoPosterior() != null ? orig.getSaldoPosterior() : 0.0)
                - (orig.getSaldoAnterior() != null ? orig.getSaldoAnterior() : 0.0);

        MovimientoCajaVirtual contra = new MovimientoCajaVirtual();
        contra.setCajaVirtual(orig.getCajaVirtual());
        contra.setTipoMovimiento(CajaVirtualTipoMovimiento.AJUSTE);
        contra.setCantidad(-efecto); // AJUSTE firmado: revierte el efecto original
        contra.setMoneda(orig.getMoneda());
        contra.setUsuario(usuario);
        contra.setReferenciaId(orig.getId());
        contra.setOrigenTipo(OrigenMovimientoTipo.ANULACION);
        contra.setOrigenId(orig.getId());
        contra.setDescripcion("ANULACION: " + (motivo != null ? motivo : "") + " (mov #" + orig.getId() + ")");
        contra.setActivo(true);
        return registrar(contra);
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
            CajaVirtualSaldo saldo = saldoRepository.findByCajaVirtualIdAndMonedaId(cajaId, e.getKey())
                    .orElseGet(() -> {
                        CajaVirtualSaldo s = new CajaVirtualSaldo();
                        s.setCajaVirtual(caja);
                        s.setMoneda(moneda);
                        return s;
                    });
            saldo.setSaldo(e.getValue());
            saldoRepository.save(saldo);
            sincronizarShim(caja, moneda, e.getValue());
        }
    }
}
