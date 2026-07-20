package com.franco.dev.domain.productos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BuscadorProductoFiltros {
    private String texto;
    private Long proveedorId;
    private Boolean activo;
    private int page;
    private int size;

    public int getFetchLimit() {
        int pageSize = size > 0 ? size : 10;
        int pageNumber = Math.max(page, 0);
        return Math.min((pageNumber + 1) * pageSize + pageSize, 200);
    }
}
