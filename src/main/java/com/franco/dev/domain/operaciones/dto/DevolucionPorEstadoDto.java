package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cantidad de devoluciones por estado, para el desglose del dashboard. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DevolucionPorEstadoDto {
    private String estado;
    private Long cantidad;
}
