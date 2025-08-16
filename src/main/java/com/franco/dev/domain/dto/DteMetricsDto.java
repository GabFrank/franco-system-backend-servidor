package com.franco.dev.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DteMetricsDto {
    private long total;
    private long pendientes;
    private long generados;
    private long enviados;
    private long aprobados;
    private long rechazados;
    private long cancelados;
}


