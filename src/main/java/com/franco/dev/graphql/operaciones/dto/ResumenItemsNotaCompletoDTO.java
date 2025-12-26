package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO completo para resumen de items de notas de recepción
 * Corresponde al tipo GraphQL ResumenItemsNota
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenItemsNotaCompletoDTO {
    
    /**
     * Total de items
     */
    private Long totalItems;
    
    /**
     * Total de cantidad
     */
    private Double totalCantidad;
    
    /**
     * Items agrupados por estado
     */
    private List<ItemPorEstadoDTO> itemsPorEstado;
    
    /**
     * Resumen por nota
     */
    private List<ResumenPorNotaDTO> resumenPorNota;
} 