package com.franco.dev.graphql.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para representar estadísticas de formas de pago
 * Utilizado para gráficos y reportes
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormaPagoEstadistica {
    private Long formaPagoId;
    private String descripcion;
    private Long cantidadTransacciones;
    private BigDecimal totalMonto;
    private BigDecimal porcentaje;
}
