package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.MovimientoBancario;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.MovimientoBancarioRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Ledger de cuentas bancarias (análogo a {@link TesoreriaService} pero para banco).
 * Aplica movimientos al saldo de la cuenta con lock pesimista y control de descubierto.
 * Es el único punto que muta {@code CuentaBancaria.saldo}.
 */
@Service
@AllArgsConstructor
public class BancoLedgerService {

    private final CuentaBancariaRepository cuentaRepository;
    private final MovimientoBancarioRepository movimientoRepository;

    private static boolean esEgreso(MovimientoBancarioTipo t) {
        return t == MovimientoBancarioTipo.SALIDA_MANUAL || t == MovimientoBancarioTipo.AJUSTE_NEGATIVO;
    }

    /**
     * Registra un movimiento bancario y aplica su efecto al saldo de la cuenta (con lock).
     * El monto llega positivo; el signo lo define el tipo.
     */
    @Transactional
    public MovimientoBancario registrar(Long cuentaId, MovimientoBancarioTipo tipo, BigDecimal monto,
                                        String descripcion, String origenTipo, Long origenId, Usuario usuario) {
        CuentaBancaria cuenta = cuentaRepository.lockById(cuentaId)
                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaId));
        BigDecimal anterior = cuenta.getSaldo() != null ? cuenta.getSaldo() : BigDecimal.ZERO;
        BigDecimal delta = esEgreso(tipo) ? monto.abs().negate() : monto.abs();
        BigDecimal nuevo = anterior.add(delta);
        boolean permiteNegativo = Boolean.TRUE.equals(cuenta.getPermiteSaldoNegativo());
        // El descubierto se controla contra el SALDO REAL de la cuenta, no contra el reservado:
        // los cheques diferidos son a futuro (se cobran en su fecha), el comercio suma saldo cada día,
        // así que el reservado es información para la decisión, no un bloqueante del pago de hoy.
        if (!permiteNegativo && esEgreso(tipo) && nuevo.compareTo(BigDecimal.ZERO) < 0) {
            throw new GraphQLException("Saldo insuficiente en la cuenta bancaria");
        }
        cuenta.setSaldo(nuevo);
        cuentaRepository.save(cuenta);

        MovimientoBancario m = new MovimientoBancario();
        m.setCuentaBancaria(cuenta);
        m.setTipoMovimiento(tipo);
        m.setMonto(monto.abs());
        m.setSaldoAnterior(anterior);
        m.setSaldoPosterior(nuevo);
        m.setDescripcion(descripcion);
        m.setOrigenTipo(origenTipo);
        m.setOrigenId(origenId);
        m.setUsuario(usuario);
        m.setAnulado(false);
        return movimientoRepository.save(m);
    }

    /**
     * Revierte un movimiento bancario posteando un AJUSTE compensatorio del signo opuesto
     * (ledger inmutable: no borra ni edita el original, solo lo marca anulado). Lo invoca el
     * módulo dueño al anular su operación. El compensatorio pasa por el control de descubierto.
     */
    @Transactional
    public MovimientoBancario revertir(MovimientoBancario orig, String motivo, Usuario usuario) {
        if (Boolean.TRUE.equals(orig.getAnulado())) {
            throw new GraphQLException("El movimiento bancario #" + orig.getId() + " ya está anulado");
        }
        // El original restó (egreso) → devolvemos con AJUSTE_POSITIVO; sumó (ingreso) → quitamos con AJUSTE_NEGATIVO.
        MovimientoBancarioTipo compensa = esEgreso(orig.getTipoMovimiento())
                ? MovimientoBancarioTipo.AJUSTE_POSITIVO
                : MovimientoBancarioTipo.AJUSTE_NEGATIVO;
        BigDecimal monto = orig.getMonto() != null ? orig.getMonto().abs() : BigDecimal.ZERO;
        MovimientoBancario contra = registrar(orig.getCuentaBancaria().getId(), compensa, monto,
                "ANULACION: " + (motivo != null ? motivo : "") + " (mov #" + orig.getId() + ")",
                OrigenMovimientoTipo.ANULACION.name(), orig.getId(), usuario);
        orig.setAnulado(true);
        movimientoRepository.save(orig);
        return contra;
    }

    /** Ajusta el saldo reservado (cheques diferidos). Positivo reserva, negativo libera. */
    @Transactional
    public void ajustarReservado(Long cuentaId, BigDecimal delta) {
        CuentaBancaria cuenta = cuentaRepository.lockById(cuentaId)
                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaId));
        BigDecimal actual = cuenta.getSaldoReservado() != null ? cuenta.getSaldoReservado() : BigDecimal.ZERO;
        cuenta.setSaldoReservado(actual.add(delta));
        cuentaRepository.save(cuenta);
    }
}
