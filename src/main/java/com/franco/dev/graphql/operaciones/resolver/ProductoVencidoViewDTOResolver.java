package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.service.media.ImagenMasterService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ProductoVencidoViewDTOResolver.class);

    @Autowired
    private ImagenMasterService imagenMasterService;

    @Autowired
    private PresentacionService presentacionService;

    /**
     * La foto de la presentación vencida y, si no tiene, la del producto
     * (misma regla que {@code ProductoResolver.imagenPrincipal}).
     * {@code null} cuando no hay ninguna: el cliente muestra el ícono.
     */
    public String imagenPrincipal(ProductoVencidoViewDTO dto) {
        if (dto == null) {
            return null;
        }
        try {
            if (dto.getPresentacionId() != null) {
                String foto = imagenMasterService.getOrMigrateImageAsBase64(TipoReferencia.PRESENTACION, dto.getPresentacionId());
                if (foto != null) {
                    return foto;
                }
            }
            if (dto.getProductoId() == null) {
                return null;
            }
            Presentacion principal = presentacionService.findByPrincipalAndProductoId(true, dto.getProductoId());
            if (principal != null && !principal.getId().equals(dto.getPresentacionId())) {
                return imagenMasterService.getOrMigrateImageAsBase64(TipoReferencia.PRESENTACION, principal.getId());
            }
            return principal == null
                    ? imagenMasterService.getOrMigrateImageAsBase64(TipoReferencia.PRODUCTO, dto.getProductoId())
                    : null;
        } catch (Exception e) {
            // Una foto que no se puede leer no rompe el reporte.
            log.warn("No se pudo resolver la imagen del vencimiento {}: {}", dto.getId(), e.getMessage());
            return null;
        }
    }
}
