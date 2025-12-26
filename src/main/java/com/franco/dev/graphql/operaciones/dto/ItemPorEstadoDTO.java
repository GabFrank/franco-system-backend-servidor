package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para items agrupados por estado
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemPorEstadoDTO {
    
    /**
     * Estado del item
     */
    private String estado;
    
    /**
     * Cantidad de items con este estado
     */
    private Long count;
    
    /**
     * Total de cantidad para este estado
     */
    private Double cantidad;
} 