package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Linea consolidada del retiro: un producto+presentacion con la cantidad total
 * sumada de todas las devoluciones SEPARADAS de una sucursal.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroLineaConsolidadaDto {
    private Long productoId;
    /** Codigo de barras principal de la presentacion (puede ser null). */
    private String codigo;
    private String descripcion;
    private String presentacion;
    /** Cantidad total en unidades de presentacion (suma de item.cantidad). */
    private Double cantidadTotal;
}
