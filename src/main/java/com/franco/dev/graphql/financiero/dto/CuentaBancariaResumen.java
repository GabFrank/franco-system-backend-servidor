package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.domain.financiero.CuentaBancaria;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Resumen de una cuenta bancaria para el sidebar del dashboard de caja:
 * saldo actual, reservado (cheques diferidos emitidos) y futuro (acreditaciones POS pendientes).
 */
@Data
@AllArgsConstructor
public class CuentaBancariaResumen {
    private CuentaBancaria cuentaBancaria;
    private BigDecimal saldo;
    private BigDecimal saldoReservado;
    private BigDecimal saldoFuturo;
}
