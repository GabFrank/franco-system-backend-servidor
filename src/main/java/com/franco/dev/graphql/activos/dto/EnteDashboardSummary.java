package com.franco.dev.graphql.activos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnteDashboardSummary {
    private Integer totalBienes;
    private Integer pagados;
    private Integer enPago;
    private Integer cuotasFaltantes;
    @Builder.Default
    private Double totalGastado = 0.0;
    @Builder.Default
    private Double totalComprometido = 0.0;
    @Builder.Default
    private Double totalPendiente = 0.0;
    private String monedaPrincipal;
}
