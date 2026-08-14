package com.franco.dev.domain.operaciones.dto;

import java.time.LocalDate;

/**
 * Proyección de {@code LoteRepository.buscarLotesDeProducto}. Es una query nativa paginada, así que
 * Spring Data mapea las columnas al getter por el nombre del alias.
 */
public interface LoteDeProductoProjection {
    Long getLoteId();
    String getNumeroLote();
    LocalDate getFechaVencimiento();
    LocalDate getFechaRetiro();
    String getEstado();
    Double getSaldo();
    Double getSaldoTotal();
}
