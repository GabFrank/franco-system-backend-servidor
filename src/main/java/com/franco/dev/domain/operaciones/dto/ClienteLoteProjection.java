package com.franco.dev.domain.operaciones.dto;

import java.sql.Timestamp;

/**
 * Proyección de {@code MovimientoStockLoteRepository.clientesPorLote}: a quién se le vendió un
 * lote, agrupado por cliente.
 *
 * Mismo criterio que {@link MovimientoLoteProjection}: query nativa y paginada, alias en camelCase
 * entre comillas dobles, y la fecha como {@link Timestamp} porque es lo que devuelve el driver.
 */
public interface ClienteLoteProjection {
    Long getClienteId();
    String getClienteNombre();
    String getClienteDocumento();
    /** Cuántas ventas distintas de este lote se le hicieron. */
    Long getVentas();
    /** Unidades del lote que se llevó, en positivo. */
    Double getCantidad();
    Timestamp getUltimaVenta();
}
