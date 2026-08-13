package com.franco.dev.domain.operaciones.dto;

import java.sql.Timestamp;

/**
 * Proyección de {@code MovimientoStockLoteRepository.clientesPorLote}: a quién se le vendió un
 * lote, una fila por venta.
 *
 * Mismo criterio que {@link MovimientoLoteProjection}: query nativa y paginada, alias en camelCase
 * entre comillas dobles, y la fecha como {@link Timestamp} porque es lo que devuelve el driver.
 */
public interface ClienteLoteProjection {
    Long getVentaId();
    /** La clave de venta es (id, sucursal_id), así que el número solo no la identifica. */
    Long getSucursalId();
    String getSucursalNombre();
    Timestamp getFecha();
    Long getClienteId();
    String getClienteNombre();
    String getClienteDocumento();
    /** Dónde ubicarlo si hay que avisarle por un recall. Nula cuando la persona no la tiene cargada. */
    String getClienteDireccion();
    /** Unidades del lote que salieron en esa venta, en positivo. */
    Double getCantidad();
}
