package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para resumen de items por nota de recepción
 * Usado para queries de resumen sin cargar todos los items
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenItemsNotaDTO {
    
    /**
     * ID de la nota de recepción
     */
    private Long notaId;
    
    /**
     * Total de items en la nota
     */
    private Long totalItems;
    
    /**
     * Total de cantidad en la nota
     */
    private Double totalCantidad;
    
    /**
     * Estado de los items
     */
    private String estado;
} 