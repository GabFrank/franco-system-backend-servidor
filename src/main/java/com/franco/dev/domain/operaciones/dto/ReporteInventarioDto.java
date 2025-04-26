package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReporteInventarioDto {
    private Long productoId;
    private String descripcion;
    private String codigoBarra;
    private Double cantidadSistema;
    private Double cantidadEncontrada;
    private Double saldo;
    private String estado;
    private String responsable;
    private String fecha;
}