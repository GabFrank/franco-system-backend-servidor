package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.service.productos.FotoProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Campos calculados de una fila del reporte de vencimientos.
 *
 * La foto no va en la vista SQL: se resuelve solo si el cliente la pide, así
 * el reporte de escritorio —que no la usa— no paga la lectura de archivos.
 */
@Component
public class ProductoVencidoViewDTOResolver implements GraphQLResolver<ProductoVencidoViewDTO> {

    @Autowired
    private FotoProductoService fotoProductoService;

    /**
     * La foto de la presentación vencida y, si no tiene, la del producto, en
     * su tamaño original. {@code null} cuando no hay ninguna: el cliente
     * muestra el ícono. Las listas piden {@code imagenPrincipalMiniatura}.
     */
    public String imagenPrincipal(ProductoVencidoViewDTO dto) {
        return foto(dto, FotoProductoService.Tamano.ORIGINAL);
    }

    public String imagenPrincipalMiniatura(ProductoVencidoViewDTO dto) {
        return foto(dto, FotoProductoService.Tamano.MINIATURA);
    }

    private String foto(ProductoVencidoViewDTO dto, FotoProductoService.Tamano tamano) {
        return dto == null ? null
                : fotoProductoService.deVencimiento(dto.getPresentacionId(), dto.getProductoId(), tamano);
    }
}
