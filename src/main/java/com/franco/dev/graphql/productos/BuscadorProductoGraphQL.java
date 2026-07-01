package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.dto.BuscadorProductoFiltros;
import com.franco.dev.domain.productos.dto.BuscadorProductoResultado;
import com.franco.dev.service.productos.ProductoProveedorService;
import com.franco.dev.service.productos.search.BuscadorProductoInteligenteService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.franco.dev.domain.productos.ProductoProveedor;

@Component
@AllArgsConstructor
public class BuscadorProductoGraphQL implements GraphQLQueryResolver {

    private final BuscadorProductoInteligenteService buscadorProductoInteligenteService;
    private final ProductoProveedorService productoProveedorService;

    public Page<BuscadorProductoResultado> buscarProductoInteligente(
            String texto,
            Long proveedorId,
            Boolean activo,
            Integer page,
            Integer size) {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 10;
        }

        BuscadorProductoFiltros filtros = BuscadorProductoFiltros.builder()
                .texto(texto)
                .proveedorId(proveedorId)
                .activo(activo)
                .page(page)
                .size(size)
                .build();

        return buscadorProductoInteligenteService.buscar(filtros);
    }

    public Page<ProductoProveedor> productoProveedorBusquedaInteligente(
            Long id,
            String texto,
            Integer page,
            Integer size,
            Long pedidoId) {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(page, size);
        return productoProveedorService.findByProveedorIdInteligente(id, texto, pedidoId, pageable);
    }
}
