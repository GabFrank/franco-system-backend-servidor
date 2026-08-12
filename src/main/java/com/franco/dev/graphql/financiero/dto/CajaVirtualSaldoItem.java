package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Saldo de efectivo de una caja por moneda (para el sidebar del dashboard). */
@Data
@AllArgsConstructor
public class CajaVirtualSaldoItem {
    private Moneda moneda;
    private BigDecimal saldo;
}
