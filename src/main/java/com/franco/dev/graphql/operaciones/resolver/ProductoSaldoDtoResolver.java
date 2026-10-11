package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.service.productos.FotoProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductoSaldoDtoResolver implements GraphQLResolver<ProductoSaldoDto> {

    @Autowired
    private FotoProductoService fotoProductoService;

    /** El original. Las listas piden {@code imagenPrincipalMiniatura}. */
    public String imagenPrincipal(ProductoSaldoDto dto) {
        return foto(dto, FotoProductoService.Tamano.ORIGINAL);
    }

    public String imagenPrincipalMiniatura(ProductoSaldoDto dto) {
        return foto(dto, FotoProductoService.Tamano.MINIATURA);
    }

    private String foto(ProductoSaldoDto dto, FotoProductoService.Tamano tamano) {
        return dto == null ? null : fotoProductoService.deProducto(dto.getProductoId(), tamano);
    }
}
