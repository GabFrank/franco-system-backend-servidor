package com.franco.dev.domain.operaciones.dto;

import java.time.LocalDate;

/**
 * Proyección de la consulta nativa de stock por lote con filtros
 * ({@code MovimientoStockLoteRepository.buscarStockPorLote}).
 *
 * Se usa una proyección y no el DTO directamente porque la query es nativa y paginada: Spring Data
 * mapea las columnas por nombre a los getters.
 */
public interface StockLoteProjection {
    Long getLoteId();
    Long getProductoId();
    String getProductoDescripcion();
    String getNumeroLote();
    LocalDate getFechaVencimiento();
    LocalDate getFechaRetiro();
    String getEstado();
    String getProveedorNombre();
    Double getCantidadDisponible();
}
