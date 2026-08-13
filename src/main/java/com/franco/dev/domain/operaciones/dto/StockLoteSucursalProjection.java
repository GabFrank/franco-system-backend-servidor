package com.franco.dev.domain.operaciones.dto;

/**
 * Proyección del desglose por sucursal de un lote
 * ({@code MovimientoStockLoteRepository.stockLotePorSucursal}).
 *
 * Los alias de la query nativa van en camelCase y entre comillas dobles por la misma razón que en
 * {@link StockLoteProjection}: Spring Data resuelve la proyección buscando el alias exacto del
 * getter, y Postgres pasa a minúsculas todo alias sin comillar.
 */
public interface StockLoteSucursalProjection {
    Long getSucursalId();
    String getSucursalNombre();
    Double getCantidadDisponible();
}
