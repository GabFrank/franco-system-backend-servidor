package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Motivo de averia mas frecuente: cantidad de items y unidades devueltas. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopMotivoDevolucionDto {
    private Long motivoId;
    private String descripcion;
    private Long items;
    private Double cantidad;
}
