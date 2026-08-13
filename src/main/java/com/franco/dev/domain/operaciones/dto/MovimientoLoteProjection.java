package com.franco.dev.domain.operaciones.dto;

import java.sql.Timestamp;

/**
 * Proyección del historial de un lote
 * ({@code MovimientoStockLoteRepository.movimientosPorLote}).
 *
 * Mismo criterio que {@link StockLoteProjection}: la query es nativa y paginada, así que Spring
 * Data mapea las columnas por nombre a los getters y los alias van en camelCase entre comillas
 * dobles.
 *
 * La fecha se declara como {@link Timestamp} y no como {@code LocalDateTime} a propósito: es el
 * tipo que devuelve el driver para una columna {@code timestamp}, así que se asigna directo. Con
 * {@code LocalDateTime} la proyección dependería de que haya un conversor registrado, y no hay
 * ninguna otra proyección nativa con fecha y hora en el proyecto que demuestre que lo hay. La
 * conversión se hace explícita al armar el DTO.
 */
public interface MovimientoLoteProjection {
    Long getId();
    Long getSucursalId();
    Timestamp getFecha();
    String getSucursalNombre();
    String getTipoMovimiento();
    Long getReferencia();
    Long getDocumentoId();
    Double getCantidad();
    String getUsuarioNombre();
}
