package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proveedor con más devoluciones: cantidad de devoluciones y valor devuelto. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopProveedorDevolucionDto {
    private Long proveedorId;
    private String nombre;
    private Long devoluciones;
    private Double valor;
}
