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
        return new HuellaPedido()
                .id(cheque.getChequera() != null ? cheque.getChequera().getId() : null)
                .numero(cheque.getTotal() != null ? BigDecimal.valueOf(cheque.getTotal()) : null)
                .bandera(cheque.getDiferido())
                .id(cheque.getMoneda() != null ? cheque.getMoneda().getId() : null)
                .id(cheque.getCuentaBancaria() != null ? cheque.getCuentaBancaria().getId() : null)
                .dia(cheque.getFechaPago())
                .texto(cheque.getConcepto())
                .calcular();
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

        // El número efectivo: el siguiente de la chequera, o el primero de su rango si todavía no emitió.
        Long desde = chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : null;
        Long hasta = chequera.getRangoHasta() != null ? chequera.getRangoHasta().longValue() : null;
        long numero = chequera.getSiguienteNumero() != null ? chequera.getSiguienteNumero() : (desde != null ? desde : 1L);
        String rango = (desde != null ? desde : "…") + "–" + (hasta != null ? hasta : "…");
        if (hasta != null && numero > hasta) {
            throw new GraphQLException("La chequera " + nombreChequera + " no tiene más números (rango " + rango + ").");
        }
        if (desde != null && numero < desde) {
            throw new GraphQLException("El próximo número de la chequera " + nombreChequera + " (" + numero
                    + ") está fuera de su rango (" + rango + ").");
        }

        cheque.setNumero((double) numero);
        cheque.setChequera(chequera);
        cheque.setUsuario(usuario);
        cheque.setCuentaBancaria(cuenta);
        BigDecimal monto = BigDecimal.valueOf(cheque.getTotal());

        if (esDiferido) {
            cheque.setEstado(EstadoCheque.DIFERIDO);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto);
        } else {
            MovimientoBancario mov = bancoLedgerService.registrar(cheque.getCuentaBancaria().getId(),
                    MovimientoBancarioTipo.SALIDA_MANUAL, monto, "Cheque " + numero + " (contado)",
                    "CHEQUE", null, usuario);
            cheque.setEstado(EstadoCheque.COBRADO);
            cheque.setFechaCobro(LocalDateTime.now());
            cheque.setMovimientoBancarioId(mov != null ? mov.getId() : null);
        }

        // Avanzar correlativo + agotar chequera
        chequera.setSiguienteNumero(numero + 1);
        if (hasta != null && numero >= hasta) {
            chequera.setEstado(EstadoChequera.AGOTADA);
        }
        chequeraService.save(chequera);
        return chequeService.save(cheque);
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
        // no cuenta dos veces este mismo cheque.
        if (Boolean.TRUE.equals(cheque.getDiferido())) {
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        MovimientoBancario mov = bancoLedgerService.registrar(cheque.getCuentaBancaria().getId(),
                MovimientoBancarioTipo.SALIDA_MANUAL, monto, "Cobro cheque " + cheque.getNumero(),
                "CHEQUE", cheque.getId(), usuario);
        cheque.setEstado(EstadoCheque.COBRADO);
        cheque.setFechaCobro(LocalDateTime.now());
        cheque.setMovimientoBancarioId(mov != null ? mov.getId() : null);
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
        if (Boolean.TRUE.equals(cheque.getDiferido()) && cheque.getEstado() == EstadoCheque.DIFERIDO
                && cheque.getCuentaBancaria() != null) {
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
        } else if (Boolean.TRUE.equals(cheque.getDiferido()) && cheque.getEstado() == EstadoCheque.DIFERIDO
                && cheque.getCuentaBancaria() != null) {
            BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        cheque.setEstado(EstadoCheque.ANULADO);
        cheque.setMotivoAnulacion(motivo);
        return chequeService.save(cheque);
    }
}
