package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Punto de la serie temporal de devoluciones (por dia). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DevolucionSeriePuntoDto {
    /** Fecha del bucket en formato YYYY-MM-DD. */
    private String fecha;
    private Long cantidad;
    private Double valor;
}
