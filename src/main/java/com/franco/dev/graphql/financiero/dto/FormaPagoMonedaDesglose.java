package com.franco.dev.graphql.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Monto recaudado por forma de pago desglosado por moneda.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormaPagoMonedaDesglose {
    private Long monedaId;
    private String denominacion;
    private String simbolo;
    private Long cantidadTransacciones;
    private BigDecimal totalMonto;
}
