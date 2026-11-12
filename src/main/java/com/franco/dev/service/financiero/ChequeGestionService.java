package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.financiero.enums.EstadoChequera;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.financiero.ChequeService;
import com.franco.dev.service.financiero.ChequeraService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ciclo de vida de cheques emitidos sobre una cuenta bancaria.
 * - Diferido: reserva el saldo (no debita hasta el cobro).
 * - Contado: debita el saldo y queda COBRADO en el acto.
 * - Cobro de diferido: debita el saldo y libera la reserva.
 * - Anulación: bloqueada si ya está cobrado; libera reserva si era diferido.
 */
@Service
@AllArgsConstructor
public class ChequeGestionService {

    private final ChequeService chequeService;
    private final ChequeraService chequeraService;
    private final com.franco.dev.repository.financiero.ChequeRepository chequeRepository;
    private final com.franco.dev.repository.financiero.ChequeraRepository chequeraRepository;
    private final com.franco.dev.repository.financiero.MovimientoBancarioRepository movimientoBancarioRepository;
    private final BancoLedgerService bancoLedgerService;
    private final IdempotenciaService idempotenciaService;
    private final javax.persistence.EntityManager entityManager;
    private final com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository pagoDetalleRepository;
    private final com.franco.dev.service.empresarial.ConfiguracionSistemaService configuracionSistema;
    private final com.franco.dev.repository.financiero.ChequeraNumeroInutilizadoRepository inutilizadoRepository;

    /** {@code configuracion_sistema}: el cheque al día queda EMITIDO hasta confirmar su cobro (issue #601). */
    static final String CLAVE_AL_DIA_EMITIDO = "cheque.al_dia_emitido";

    /** Nombre de la operación de la emisión en cuotas en {@code financiero.operacion_idempotente}. */
    static final String OPERACION_EMITIR_CHEQUES = "EMITIR_CHEQUES";

    /**
     * Si la bandera está prendida. Apagada (o sin fila) = el cheque al día se debita al emitirse, como siempre. Se
     * prende recién con el central y el desktop de esta versión en el canal: un desktop anterior no cobra un EMITIDO.
     */
    boolean alDiaQuedaEmitido() {
        return configuracionSistema.valor(CLAVE_AL_DIA_EMITIDO).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(false);
    }

    /** Un cheque con el monto reservado en la cuenta: el diferido y el al día emitido (issue #601). */
    static boolean reserva(Cheque cheque) {
        return cheque.getEstado() == EstadoCheque.DIFERIDO || cheque.getEstado() == EstadoCheque.EMITIDO;
    }

    /** Nombre de la operacion en {@code financiero.operacion_idempotente}. */
    static final String OPERACION_EMITIR_CHEQUE = "EMITIR_CHEQUE";

    /**
     * Emite un cheque suelto con clave de idempotencia (issue #376): repetir el pedido emitia otro cheque
     * con el numero siguiente y volvia a debitar o reservar. Con la clave, el pedido repetido devuelve el
     * cheque que emitio el original. Clave nula = cliente viejo, sin cambios.
     *
     * <p>Los cheques de un pago no pasan por aca: los cubre la clave del pago.</p>
     */
    @Transactional
    public Cheque emitir(Cheque cheque, Usuario usuario, String claveIdempotencia) {
        // Antes de la huella: un total que no es un número la rompe con un error que no dice nada.
        validarTotal(cheque);
        return idempotenciaService.ejecutar(claveIdempotencia, OPERACION_EMITIR_CHEQUE, huellaDe(cheque), usuario,
                () -> emitir(cheque, usuario),
                Cheque::getId,
                this::chequeYaEmitido);
    }

    /** numeric(18,4): 14 enteros. */
    private static final BigDecimal TOPE = new BigDecimal("100000000000000");

    private static void validarTotal(Cheque cheque) {
        Double total = cheque.getTotal();
        if (total == null || total.isNaN() || total.isInfinite() || total <= 0) {
            throw new GraphQLException("El monto del cheque debe ser mayor a cero.");
        }
        if (BigDecimal.valueOf(total).compareTo(TOPE) >= 0) throw new GraphQLException("Monto del cheque fuera de rango.");
    }

    /** El cheque del pedido original. Si despues se anulo, se rechaza: ni exito ni otra emision. */
    private Cheque chequeYaEmitido(Long chequeId) {
        Cheque cheque = chequeRepository.findById(chequeId).orElse(null);
        if (cheque != null && cheque.getEstado() == EstadoCheque.ANULADO) {
            throw new GraphQLException("El cheque de este pedido ya se emitió y después fue anulado."
                    + " Si corresponde, emita un cheque nuevo.");
        }
        return cheque;
    }

    /** Huella del pedido de emision. {@code fechaPago} cuenta solo por su dia. */
    static String huellaDe(Cheque cheque) {
        HuellaPedido h = new HuellaPedido()
                .id(cheque.getChequera() != null ? cheque.getChequera().getId() : null)
                .numero(cheque.getTotal() != null ? BigDecimal.valueOf(cheque.getTotal()) : null)
                .bandera(cheque.getDiferido())
                .id(cheque.getMoneda() != null ? cheque.getMoneda().getId() : null)
                .id(cheque.getCuentaBancaria() != null ? cheque.getCuentaBancaria().getId() : null)
                .dia(cheque.getFechaPago())
                .texto(cheque.getConcepto());
        // Los datos de la emisión completa (issue #601), solo si vinieron: así la huella de un pedido de un desktop
        // anterior queda igual que antes.
        if (cheque.getProveedor() != null) h.id(cheque.getProveedor().getId());
        if (cheque.getBeneficiario() != null) h.texto(cheque.getBeneficiario());
        if (cheque.getNominal() != null) h.bandera(cheque.getNominal());
        if (cheque.getFechaEntrega() != null) h.dia(cheque.getFechaEntrega());
        return h.calcular();
    }

    /**
     * Emite un cheque. Avanza el correlativo de la chequera; agota la chequera si corresponde.
     *
     * <p>La chequera se toma con lock y <b>se relee de la base</b>: quien llama ya la cargó para armar el
     * cheque, y el lock devuelve esa instancia sin refrescar. Con el número siguiente de antes de
     * esperar, dos emisiones simultáneas salían con el mismo número (issue #376).</p>
     *
     * <p>Todo lo que hace inválido al cheque se rechaza acá, antes de mover nada: lo validaba solo el
     * desktop, y lo usan dos caminos (la emisión suelta y el pago a proveedores).</p>
     */
    @Transactional
    public Cheque emitir(Cheque cheque, Usuario usuario) {
        Chequera chequera = cheque.getChequera();
        if (chequera == null) throw new GraphQLException("El cheque requiere una chequera");
        validarTotal(cheque);
        // Lock pesimista de la chequera: serializa el avance del correlativo (evita números duplicados).
        chequera = chequeraRepository.lockById(chequera.getId()).orElseThrow(
                () -> new GraphQLException("Chequera no encontrada"));
        entityManager.refresh(chequera);
        if (chequera.getEstado() != null && chequera.getEstado() != EstadoChequera.ACTIVA) {
            throw new GraphQLException("La chequera no está activa (" + chequera.getEstado() + ")");
        }
        String nombreChequera = chequera.getNombre() != null ? chequera.getNombre() : ("#" + chequera.getId());

        // La cuenta es la de la chequera: un cheque no sale de una cuenta con la chequera de otra.
        com.franco.dev.domain.financiero.CuentaBancaria cuenta = chequera.getCuentaBancaria();
        if (cuenta == null) {
            throw new GraphQLException("La chequera " + nombreChequera + " no tiene una cuenta bancaria asignada.");
        }
        if (cheque.getCuentaBancaria() != null && !cuenta.getId().equals(cheque.getCuentaBancaria().getId())) {
            throw new GraphQLException("La cuenta del cheque no es la de la chequera " + nombreChequera + ".");
        }
        if (cheque.getMoneda() != null && cuenta.getMoneda() != null
                && !cuenta.getMoneda().getId().equals(cheque.getMoneda().getId())) {
            throw new GraphQLException("La moneda del cheque no es la de la cuenta de la chequera " + nombreChequera + ".");
        }
        // Sin moneda (la emisión en lote no la manda), la de la cuenta: un cheque sin moneda no se puede mostrar ni
        // sumar por moneda (encontrado en la prueba de UI, 2026-11-12).
        if (cheque.getMoneda() == null) cheque.setMoneda(cuenta.getMoneda());

        // La fecha de emisión siempre queda (issue #601): sin ella el cheque no aparecía en «Emitidos hoy» y la
        // columna Emisión salía vacía en los emitidos sueltos.
        if (cheque.getFechaEntrega() == null) cheque.setFechaEntrega(LocalDateTime.now());
        if (cheque.getBeneficiario() == null && cheque.getProveedor() != null && cheque.getProveedor().getPersona() != null) {
            cheque.setBeneficiario(cheque.getProveedor().getPersona().getNombre());
        }

        boolean esDiferido = Boolean.TRUE.equals(cheque.getDiferido());
        if (esDiferido) {
            if (cheque.getFechaPago() == null) throw new GraphQLException("Un cheque diferido requiere fecha de pago.");
            // Contra el día de emisión, no contra hoy: el pago a proveedores permite registrar con fecha
            // retroactiva un cheque que ya se entregó.
            java.time.LocalDate emision = (cheque.getFechaEntrega() != null ? cheque.getFechaEntrega() : LocalDateTime.now()).toLocalDate();
            if (cheque.getFechaPago().toLocalDate().isBefore(emision)) {
                throw new GraphQLException("La fecha de pago de un cheque diferido no puede ser anterior a su emisión.");
            }
        }

        // El número efectivo: el siguiente de la chequera (o el primero de su rango si todavía no emitió), salteando
        // los inutilizados vigentes y los que ya tienen cheque (issue #603).
        Long desde = chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : null;
        Long hasta = chequera.getRangoHasta() != null ? chequera.getRangoHasta().longValue() : null;
        long base = chequera.getSiguienteNumero() != null ? chequera.getSiguienteNumero() : (desde != null ? desde : 1L);
        long numero = primerLibre(chequera, base);
        String rango = (desde != null ? desde : "…") + "–" + (hasta != null ? hasta : "…");
        if (hasta != null && numero > hasta) {
            throw new GraphQLException("La chequera " + nombreChequera + " no tiene más números (rango " + rango + ").");
        }
        if (desde != null && numero < desde) {
            throw new GraphQLException("El próximo número de la chequera " + nombreChequera + " (" + numero
                    + ") está fuera de su rango (" + rango + ").");
        }

        // Al día: su fecha de pago es la de emisión. Sin ella no aparecía en el dashboard de cheques, que
        // filtra por esa fecha.
        if (!esDiferido && cheque.getFechaPago() == null) {
            cheque.setFechaPago(cheque.getFechaEntrega() != null ? cheque.getFechaEntrega() : LocalDateTime.now());
        }

        cheque.setNumero((double) numero);
        cheque.setChequera(chequera);
        cheque.setUsuario(usuario);
        cheque.setCuentaBancaria(cuenta);
        BigDecimal monto = BigDecimal.valueOf(cheque.getTotal());

        if (esDiferido) {
            cheque.setEstado(EstadoCheque.DIFERIDO);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto);
        } else if (alDiaQuedaEmitido()) {
            // Al día, entregado y todavía no debitado: reserva como un diferido y se debita al confirmar el cobro.
            // Los fondos no bloquean en esta etapa (decidido 2026-11-12): el aviso va en la respuesta.
            cheque.setEstado(EstadoCheque.EMITIDO);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto);
            // El aviso compara contra el saldo real, que es lo que controla el cobro (BancoLedgerService.registrar):
            // lo reservado por otros cheques no bloquea a propósito.
            BigDecimal saldo = cuenta.getSaldo() != null ? cuenta.getSaldo() : BigDecimal.ZERO;
            if (saldo.compareTo(monto) < 0) {
                cheque.setAvisoFondos("El saldo de la cuenta no alcanza para este cheque: se emitió igual y se va a"
                        + " rechazar al cobrarlo si para entonces no hay fondos.");
            }
        } else {
            MovimientoBancario mov = bancoLedgerService.registrar(cheque.getCuentaBancaria().getId(),
                    MovimientoBancarioTipo.SALIDA_MANUAL, monto, "Cheque " + numero + " (contado)",
                    "CHEQUE", null, usuario);
            cheque.setEstado(EstadoCheque.COBRADO);
            cheque.setFechaCobro(LocalDateTime.now());
            cheque.setMovimientoBancarioId(mov != null ? mov.getId() : null);
        }

        // Avanzar correlativo + agotar chequera: agotada cuando no queda ningún número libre en el rango (los
        // inutilizados del final no cuentan como hojas).
        chequera.setSiguienteNumero(numero + 1);
        if (hasta != null && (numero >= hasta || hojasLibres(chequera) == 0)) {
            chequera.setEstado(EstadoChequera.AGOTADA);
        }
        chequeraService.save(chequera);
        try {
            return chequeService.save(cheque);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // El índice único de (chequera, número): no debería pasar, pero si pasa que se entienda. La
            // transacción ya quedó marcada para deshacerse.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_cheque_chequera_numero")) {
                throw new GraphQLException("El número " + numero + " de la chequera " + nombreChequera
                        + " ya existe. Revisá su correlativo.");
            }
            throw e;
        }
    }

    /** Una cuota de la emisión en lote: monto y fecha de pago. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CuotaCheque {
        private Double total;
        private LocalDateTime fechaPago;
    }

    /** Datos comunes de los cheques de un lote. */
    @lombok.Data
    public static class DatosLote {
        private Long chequeraId;
        private Boolean diferido;
        private Boolean nominal;
        private String beneficiario;
        private com.franco.dev.domain.personas.Proveedor proveedor;
        private LocalDateTime fechaEmision;
        private String concepto;
    }

    /** Tope de cheques por emisión (el desktop ofrece 1 a 12). */
    static final int MAXIMO_CUOTAS = 24;

    /**
     * Emite N cheques de una chequera en una sola transacción (issue #601): todos o ninguno, con números de la chequera
     * en orden y una sola clave de idempotencia para el lote (el reenvío devuelve los mismos N). Antes el desktop habría
     * tenido que llamar N veces a emitirCheque, y un corte a la mitad dejaba cheques sueltos.
     *
     * <p>Cada cheque pasa por {@link #emitir(Cheque, Usuario)}: mismas validaciones, mismo lock de la chequera (se toma
     * acá una vez y el resto lo reusa dentro de la transacción) y mismo estado según diferido / al día.</p>
     */
    @Transactional
    public List<Cheque> emitirLote(DatosLote datos, List<CuotaCheque> cuotas, Usuario usuario, String claveIdempotencia) {
        if (cuotas == null || cuotas.isEmpty()) throw new GraphQLException("Indicá al menos un cheque.");
        if (cuotas.size() > MAXIMO_CUOTAS) throw new GraphQLException("Como máximo " + MAXIMO_CUOTAS + " cheques por emisión.");
        if (datos.getChequeraId() == null) throw new GraphQLException("El cheque requiere una chequera");
        HuellaPedido h = new HuellaPedido().id(datos.getChequeraId()).bandera(datos.getDiferido()).bandera(datos.getNominal())
                .texto(datos.getBeneficiario()).id(datos.getProveedor() != null ? datos.getProveedor().getId() : null)
                .dia(datos.getFechaEmision()).texto(datos.getConcepto());
        for (CuotaCheque c : cuotas) {
            h.numero(c.getTotal() != null ? BigDecimal.valueOf(c.getTotal()) : null).dia(c.getFechaPago());
        }
        return idempotenciaService.ejecutar(claveIdempotencia, OPERACION_EMITIR_CHEQUES, h.calcular(), usuario,
                () -> emitirTodos(datos, cuotas, usuario),
                lista -> lista.get(0).getId(),
                loteId -> chequeRepository.findByLoteIdOrderByNumeroAsc(loteId));
    }

    private List<Cheque> emitirTodos(DatosLote datos, List<CuotaCheque> cuotas, Usuario usuario) {
        Chequera chequera = chequeraRepository.lockById(datos.getChequeraId())
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada"));
        entityManager.refresh(chequera);
        long libres = hojasLibres(chequera);
        if (libres < cuotas.size()) {
            String nombre = chequera.getNombre() != null ? chequera.getNombre() : ("#" + chequera.getId());
            throw new GraphQLException("La chequera " + nombre + " tiene " + libres + " hoja(s) libre(s): no alcanzan para "
                    + cuotas.size() + " cheques.");
        }
        LocalDateTime emision = datos.getFechaEmision() != null ? datos.getFechaEmision() : LocalDateTime.now();
        List<Cheque> out = new java.util.ArrayList<>();
        Long loteId = null;
        for (CuotaCheque cuota : cuotas) {
            Cheque c = new Cheque();
            c.setChequera(chequera);
            c.setTotal(cuota.getTotal());
            c.setDiferido(Boolean.TRUE.equals(datos.getDiferido()));
            c.setFechaPago(cuota.getFechaPago());
            c.setFechaEntrega(emision);
            c.setNominal(datos.getNominal() != null ? datos.getNominal() : Boolean.TRUE);
            c.setBeneficiario(datos.getBeneficiario());
            c.setProveedor(datos.getProveedor());
            c.setConcepto(datos.getConcepto());
            Cheque emitido = emitir(c, usuario);
            if (loteId == null) loteId = emitido.getId();
            emitido.setLoteId(loteId);
            out.add(chequeService.save(emitido));
        }
        return out;
    }

    // ── Numeración (issue #603) ──

    /** Próximo número de la chequera: el siguiente, o el primero del rango si todavía no emitió. */
    private static long base(Chequera chequera) {
        long desde = chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : 1L;
        return chequera.getSiguienteNumero() != null ? chequera.getSiguienteNumero() : desde;
    }

    /** Números desde {@code desde} que no se pueden usar: inutilizados vigentes y los que ya tienen un cheque. */
    private java.util.Set<Long> ocupados(Chequera chequera, long desde) {
        java.util.Set<Long> out = new java.util.HashSet<>(inutilizadoRepository.numerosVigentesDesde(chequera.getId(), desde));
        for (Double n : chequeRepository.numerosDesde(chequera.getId(), (double) desde)) {
            if (n != null) out.add(n.longValue());
        }
        return out;
    }

    /** El primer número libre desde {@code desde}. Puede pasarse del rango: quien llama lo controla. */
    long primerLibre(Chequera chequera, long desde) {
        java.util.Set<Long> ocupados = ocupados(chequera, desde);
        long n = desde;
        while (ocupados.contains(n)) n++;
        return n;
    }

    /**
     * Los próximos {@code cantidad} números libres (para el «Próximo Nº» y el plan de cuotas del desktop). Se corta en
     * el fin del rango: puede devolver menos.
     */
    public List<Long> proximosLibres(Chequera chequera, int cantidad) {
        long desde = base(chequera);
        Long hasta = chequera.getRangoHasta() != null ? chequera.getRangoHasta().longValue() : null;
        java.util.Set<Long> ocupados = ocupados(chequera, desde);
        List<Long> out = new java.util.ArrayList<>();
        for (long n = desde; out.size() < cantidad && (hasta == null || n <= hasta); n++) {
            if (!ocupados.contains(n)) out.add(n);
        }
        return out;
    }

    /** Hojas libres desde el próximo número hasta el fin del rango, sin los inutilizados. Sin rango = sin límite. */
    public long hojasLibres(Chequera chequera) {
        if (chequera.getRangoHasta() == null) return Long.MAX_VALUE;
        long desde = base(chequera);
        long hasta = chequera.getRangoHasta().longValue();
        if (hasta < desde) return 0;
        long ocupadosEnRango = ocupados(chequera, desde).stream().filter(n -> n <= hasta).count();
        return Math.max(0, hasta - desde + 1 - ocupadosEnRango);
    }

    // ── Inutilizar números (issue #603, desk #720) ──

    /**
     * Inutiliza un número de la chequera. Futuro: se saltea al emitir. Pasado sin cheque: queda registrado. Con cheque:
     * {@code ANULADO} lo anula (solo si es suelto: el de un pago se revierte con el pago) o {@code RENUMERADO} lo pasa a
     * {@code numeroNuevo}, que tiene que estar libre y en el rango.
     *
     * <p>Locks en el orden del módulo: chequera → cheque → cuenta.</p>
     */
    @Transactional
    public com.franco.dev.domain.financiero.ChequeraNumeroInutilizado inutilizar(Long chequeraId, Long numero, String motivo,
            com.franco.dev.domain.financiero.enums.AccionChequeInutilizado accion, Long numeroNuevo, Usuario usuario) {
        if (numero == null) throw new GraphQLException("Indicá el número a inutilizar.");
        if (motivo == null || motivo.trim().isEmpty()) throw new GraphQLException("El motivo es obligatorio.");
        Chequera chequera = chequeraRepository.lockById(chequeraId)
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada"));
        entityManager.refresh(chequera);
        exigirEnRango(chequera, numero);
        if (inutilizadoRepository.findFirstByChequeraIdAndNumeroAndRevertidoEnIsNull(chequeraId, numero).isPresent()) {
            throw new GraphQLException("El número " + numero + " ya está inutilizado.");
        }
        com.franco.dev.domain.financiero.enums.AccionChequeInutilizado a =
                accion != null ? accion : com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.NINGUNA;
        Cheque cheque = chequeRepository.findFirstByChequeraIdAndNumero(chequeraId, numero.doubleValue()).orElse(null);
        if (cheque != null && cheque.getEstado() != EstadoCheque.ANULADO
                && a == com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.NINGUNA) {
            throw new GraphQLException("El número " + numero + " tiene un cheque: elegí anularlo o pasarlo a otro número.");
        }
        if (cheque == null && a != com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.NINGUNA) {
            a = com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.NINGUNA;
        }
        String razon = "Número inutilizado: " + motivo.trim();
        if (a == com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.ANULADO) {
            pagoDetalleRepository.findFirstByChequeIdOrderByIdAsc(cheque.getId())
                    .filter(d -> !Boolean.TRUE.equals(d.getAnulado()))
                    .ifPresent(d -> { throw new GraphQLException("El cheque Nº " + numero + " es del pago #" + d.getPagoId()
                            + ": revertí el pago desde la caja mayor, o pasá el cheque a otro número."); });
            // Misma lógica que la anulación por pago: libera la reserva o revierte el débito si ya era COBRADO.
            anularPorPago(cheque.getId(), razon, usuario);
        } else if (a == com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.RENUMERADO) {
            if (numeroNuevo == null) throw new GraphQLException("Indicá el número al que pasa el cheque.");
            exigirEnRango(chequera, numeroNuevo);
            if (numeroNuevo.equals(numero)
                    || inutilizadoRepository.findFirstByChequeraIdAndNumeroAndRevertidoEnIsNull(chequeraId, numeroNuevo).isPresent()
                    || chequeRepository.findFirstByChequeraIdAndNumero(chequeraId, numeroNuevo.doubleValue()).isPresent()) {
                throw new GraphQLException("El número " + numeroNuevo + " no está libre.");
            }
            Cheque bloqueado = chequeRepository.lockById(cheque.getId()).orElse(cheque);
            bloqueado.setNumero(numeroNuevo.doubleValue());
            chequeService.save(bloqueado);
            // Si el número nuevo era el próximo, el correlativo lo saltea solo (primerLibre mira los cheques).
        }
        com.franco.dev.domain.financiero.ChequeraNumeroInutilizado i = new com.franco.dev.domain.financiero.ChequeraNumeroInutilizado();
        i.setChequera(chequera);
        i.setNumero(numero);
        i.setMotivo(motivo.trim().toUpperCase());
        i.setUsuario(usuario);
        i.setCheque(cheque);
        i.setAccionCheque(a);
        i.setNumeroNuevo(a == com.franco.dev.domain.financiero.enums.AccionChequeInutilizado.RENUMERADO ? numeroNuevo : null);
        com.franco.dev.domain.financiero.ChequeraNumeroInutilizado guardado = inutilizadoRepository.save(i);
        actualizarAgotada(chequera);
        return guardado;
    }

    /** Revierte una inutilización: el número vuelve a estar libre. No revive el cheque anulado ni deshace el renumerado. */
    @Transactional
    public com.franco.dev.domain.financiero.ChequeraNumeroInutilizado revertirInutilizacion(Long id, Usuario usuario) {
        com.franco.dev.domain.financiero.ChequeraNumeroInutilizado i = inutilizadoRepository.findById(id)
                .orElseThrow(() -> new GraphQLException("Inutilización no encontrada: " + id));
        if (i.getRevertidoEn() != null) throw new GraphQLException("La inutilización ya estaba revertida.");
        Chequera chequera = chequeraRepository.lockById(i.getChequera().getId())
                .orElseThrow(() -> new GraphQLException("Chequera no encontrada"));
        entityManager.refresh(chequera);
        i.setRevertidoEn(LocalDateTime.now());
        i.setRevertidoPor(usuario);
        com.franco.dev.domain.financiero.ChequeraNumeroInutilizado guardado = inutilizadoRepository.save(i);
        actualizarAgotada(chequera);
        return guardado;
    }

    private static void exigirEnRango(Chequera chequera, Long numero) {
        Long desde = chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : null;
        Long hasta = chequera.getRangoHasta() != null ? chequera.getRangoHasta().longValue() : null;
        if ((desde != null && numero < desde) || (hasta != null && numero > hasta)) {
            throw new GraphQLException("El número " + numero + " está fuera del rango de la chequera ("
                    + (desde != null ? desde : "…") + "–" + (hasta != null ? hasta : "…") + ").");
        }
    }

    /** ACTIVA ↔ AGOTADA según queden números libres (inutilizar el último la agota; revertirlo la reactiva). */
    private void actualizarAgotada(Chequera chequera) {
        if (chequera.getRangoHasta() == null) return;
        boolean sinLibres = hojasLibres(chequera) == 0;
        if (sinLibres && chequera.getEstado() == EstadoChequera.ACTIVA) {
            chequera.setEstado(EstadoChequera.AGOTADA);
            chequeraService.save(chequera);
        } else if (!sinLibres && chequera.getEstado() == EstadoChequera.AGOTADA) {
            chequera.setEstado(EstadoChequera.ACTIVA);
            chequeraService.save(chequera);
        }
    }

    /** Cobra un cheque diferido: debita el saldo real y libera la reserva. */
    @Transactional
    public Cheque cobrar(Long chequeId, Usuario usuario) {
        Cheque cheque = chequeRepository.lockById(chequeId).orElseThrow(
                () -> new GraphQLException("Cheque no encontrado: " + chequeId));
        if (cheque.getEstado() == EstadoCheque.COBRADO) throw new GraphQLException("El cheque ya está cobrado");
        if (cheque.getEstado() == EstadoCheque.ANULADO) throw new GraphQLException("El cheque está anulado");
        BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
        // Liberar la reserva ANTES de debitar: así el control de descubierto (saldo - reservado)
        // no cuenta dos veces este mismo cheque. Por estado, no por el flag diferido: el al día EMITIDO
        // también reservó (issue #601).
        if (reserva(cheque)) {
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        MovimientoBancario mov = bancoLedgerService.registrar(cheque.getCuentaBancaria().getId(),
                MovimientoBancarioTipo.SALIDA_MANUAL, monto, "Cobro cheque " + (cheque.getNumero() != null ? cheque.getNumero().longValue() : cheque.getId()),
                "CHEQUE", cheque.getId(), usuario);
        cheque.setEstado(EstadoCheque.COBRADO);
        cheque.setFechaCobro(LocalDateTime.now());
        cheque.setMovimientoBancarioId(mov != null ? mov.getId() : null);
        // Si el cheque salió de un pago, la fila del banco tiene que seguir diciendo de qué pago es (pagoIdLeido sale de
        // este vínculo). Es solo para mostrar: anular el pago revierte por cheque.movimientoBancarioId, una sola vez.
        if (mov != null && cheque.getId() != null) {
            pagoDetalleRepository.findFirstByChequeIdOrderByIdAsc(cheque.getId())
                    .filter(d -> d.getMovimientoBancarioId() == null)
                    .ifPresent(d -> { d.setMovimientoBancarioId(mov.getId()); pagoDetalleRepository.save(d); });
        }
        return chequeService.save(cheque);
    }

    /** Anula un cheque no cobrado. Libera la reserva si era diferido. */
    @Transactional
    public Cheque anular(Long chequeId, String motivo, Usuario usuario) {
        Cheque cheque = chequeRepository.lockById(chequeId).orElseThrow(
                () -> new GraphQLException("Cheque no encontrado: " + chequeId));
        if (cheque.getEstado() == EstadoCheque.COBRADO) {
            throw new GraphQLException("No se puede anular un cheque ya cobrado");
        }
        // Un cheque de un pago no se anula suelto: el pago quedaría pagado con un cheque que no existe (issue #601).
        pagoDetalleRepository.findFirstByChequeIdOrderByIdAsc(chequeId)
                .filter(d -> !Boolean.TRUE.equals(d.getAnulado()))
                .ifPresent(d -> { throw new GraphQLException("Este cheque es del pago #" + d.getPagoId()
                        + ": anulá el pago desde la caja mayor, que anula el cheque con él."); });
        if (reserva(cheque) && cheque.getCuentaBancaria() != null) {
            BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        cheque.setEstado(EstadoCheque.ANULADO);
        cheque.setMotivoAnulacion(motivo);
        return chequeService.save(cheque);
    }

    /**
     * Anula un cheque como parte de la reversión de un pago CPP. A diferencia de {@link #anular},
     * también revierte los cheques al contado (COBRADO): revierte su movimiento bancario en vez de
     * bloquear. Diferido no cobrado → libera la reserva. Idempotente si ya está anulado.
     */
    @Transactional
    public Cheque anularPorPago(Long chequeId, String motivo, Usuario usuario) {
        Cheque cheque = chequeRepository.lockById(chequeId).orElseThrow(
                () -> new GraphQLException("Cheque no encontrado: " + chequeId));
        if (cheque.getEstado() == EstadoCheque.ANULADO) return cheque;
        if (cheque.getEstado() == EstadoCheque.COBRADO) {
            if (cheque.getMovimientoBancarioId() != null) {
                movimientoBancarioRepository.findById(cheque.getMovimientoBancarioId())
                        .ifPresent(mb -> bancoLedgerService.revertir(mb, motivo, usuario));
            }
        } else if (reserva(cheque) && cheque.getCuentaBancaria() != null) {
            BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        cheque.setEstado(EstadoCheque.ANULADO);
        cheque.setMotivoAnulacion(motivo);
        return chequeService.save(cheque);
    }
}
