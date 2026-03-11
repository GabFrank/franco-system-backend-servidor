package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para resumen por nota de recepción
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenPorNotaDTO {
    
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
     * Estado de la nota
     */
    private String estado;
} 