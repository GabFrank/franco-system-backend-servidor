package com.franco.dev.domain.operaciones.dto;

/**
 * Proyección de {@code MovimientoStockLoteRepository.resumenMostradorLote}.
 *
 * Mismo criterio que el resto de las proyecciones nativas del ledger de lotes: alias en camelCase
 * entre comillas dobles para que Spring Data los matchee con los getters.
 */
public interface MostradorLoteProjection {
    Long getVentas();
    Double getCantidad();
}
