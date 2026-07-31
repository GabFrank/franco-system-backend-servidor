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
    private final BancoLedgerService bancoLedgerService;

    /** Emite un cheque. Avanza el correlativo de la chequera; agota la chequera si corresponde. */
    @Transactional
    public Cheque emitir(Cheque cheque, Usuario usuario) {
        Chequera chequera = cheque.getChequera();
        if (chequera == null) throw new GraphQLException("El cheque requiere una chequera");
        chequera = chequeraService.findById(chequera.getId()).orElseThrow(
                () -> new GraphQLException("Chequera no encontrada"));
        if (chequera.getEstado() != null && chequera.getEstado() != EstadoChequera.ACTIVA) {
            throw new GraphQLException("La chequera no está activa (" + chequera.getEstado() + ")");
        }
        long numero = chequera.getSiguienteNumero() != null ? chequera.getSiguienteNumero()
                : (chequera.getRangoDesde() != null ? chequera.getRangoDesde().longValue() : 1L);
        cheque.setNumero((double) numero);
        cheque.setChequera(chequera);
        cheque.setUsuario(usuario);
        if (cheque.getCuentaBancaria() == null && chequera.getCuentaBancaria() != null) {
            cheque.setCuentaBancaria(chequera.getCuentaBancaria());
        }
        BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
        boolean diferido = Boolean.TRUE.equals(cheque.getDiferido());

        if (diferido) {
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
        if (chequera.getRangoHasta() != null && numero >= chequera.getRangoHasta().longValue()) {
            chequera.setEstado(EstadoChequera.AGOTADA);
        }
        chequeraService.save(chequera);
        return chequeService.save(cheque);
    }

    /** Cobra un cheque diferido: debita el saldo real y libera la reserva. */
    @Transactional
    public Cheque cobrar(Long chequeId, Usuario usuario) {
        Cheque cheque = chequeService.findById(chequeId).orElseThrow(
                () -> new GraphQLException("Cheque no encontrado: " + chequeId));
        if (cheque.getEstado() == EstadoCheque.COBRADO) throw new GraphQLException("El cheque ya está cobrado");
        if (cheque.getEstado() == EstadoCheque.ANULADO) throw new GraphQLException("El cheque está anulado");
        BigDecimal monto = BigDecimal.valueOf(cheque.getTotal() != null ? cheque.getTotal() : 0.0);
        MovimientoBancario mov = bancoLedgerService.registrar(cheque.getCuentaBancaria().getId(),
                MovimientoBancarioTipo.SALIDA_MANUAL, monto, "Cobro cheque " + cheque.getNumero(),
                "CHEQUE", cheque.getId(), usuario);
        if (Boolean.TRUE.equals(cheque.getDiferido())) {
            bancoLedgerService.ajustarReservado(cheque.getCuentaBancaria().getId(), monto.negate());
        }
        cheque.setEstado(EstadoCheque.COBRADO);
        cheque.setFechaCobro(LocalDateTime.now());
        cheque.setMovimientoBancarioId(mov != null ? mov.getId() : null);
        return chequeService.save(cheque);
    }

    /** Anula un cheque no cobrado. Libera la reserva si era diferido. */
    @Transactional
    public Cheque anular(Long chequeId, String motivo, Usuario usuario) {
        Cheque cheque = chequeService.findById(chequeId).orElseThrow(
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
}
