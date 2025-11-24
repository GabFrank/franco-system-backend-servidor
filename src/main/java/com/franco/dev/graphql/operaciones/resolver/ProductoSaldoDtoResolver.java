package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.media.ImagenMasterService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductoSaldoDtoResolver implements GraphQLResolver<ProductoSaldoDto> {

    @Autowired
    private ImagenMasterService imagenMasterService;

    @Autowired
    private PresentacionService presentacionService;

    public String imagenPrincipal(ProductoSaldoDto dto) {
        if (dto == null || dto.getProductoId() == null) {
            return null;
        }

        try {
            // Get the principal presentation for the product
            Presentacion presentacionPrincipal = presentacionService.findByPrincipalAndProductoId(true, dto.getProductoId());
            
            if (presentacionPrincipal != null) {
                // Try to get the image using the new ImagenMasterService with backward compatibility
                return imagenMasterService.getOrMigrateImageAsBase64(TipoReferencia.PRESENTACION, presentacionPrincipal.getId());
            } else {
                // If no principal presentation, try to get image directly for the product
                return imagenMasterService.getOrMigrateImageAsBase64(TipoReferencia.PRODUCTO, dto.getProductoId());
            }
        } catch (Exception e) {
            // Return null if there's any error getting the image
            return null;
        }
    }
}

