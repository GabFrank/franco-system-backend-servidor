package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
    private List<FormaPagoMonedaDesglose> desgloseMoneda = new ArrayList<>();
    private List<DesglosePeriodoGrafico> desglosePeriodos = new ArrayList<>();
    private List<DesgloseAnhoGrafico> desgloseAnhos = new ArrayList<>();
}
