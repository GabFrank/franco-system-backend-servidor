package com.franco.dev.graphql.productos.input;

import lombok.Data;

import java.util.List;

@Data
public class PrecioEspecialSucursalInput {
    private Long precioId;
    private List<Long> sucursalIds;
    private Double precio;
    /** yyyy-MM-dd o null. */
    private String fechaDesde;
    /** yyyy-MM-dd o null. */
    private String fechaHasta;
}
