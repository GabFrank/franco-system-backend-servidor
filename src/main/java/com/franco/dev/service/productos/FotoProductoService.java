package com.franco.dev.service.productos;

import com.franco.dev.domain.media.ImagenMaster;
import com.franco.dev.domain.media.enums.TipoReferencia;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.media.ImagenMasterRepository;
import com.franco.dev.service.utils.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * La foto de un producto o de una presentacion, en el tamano que la pantalla dibuja.
 *
 * <p>Es la unica regla de resolucion: los resolvers de {@code Producto}, {@code Presentacion},
 * {@code ProductoSaldoDto} y {@code ProductoVencidoView} delegan aca (issue #263).</p>
 *
 * <p>La foto vigente es la del directorio de presentaciones, que es donde escribe la subida.
 * {@code imagen_master} solo se consulta cuando ahi no hay archivo, y nunca se escribe: leer una
 * foto no copia archivos ni inserta filas. Sin foto se devuelve {@code null}, no un relleno.</p>
 */
@Service
public class FotoProductoService {

    private static final Logger log = LoggerFactory.getLogger(FotoProductoService.class);

    public enum Tamano {
        /** 250x250, para iconos, avatares y cards. */
        MINIATURA,
        /** Lado mayor de hasta {@link ImageService#LADO_MEDIANA} px, para vistas grandes. */
        MEDIANA,
        ORIGINAL
    }

    @Autowired
    private ImageService imageService;

    @Autowired
    private ImagenMasterRepository imagenMasterRepository;

    @Autowired
    private PresentacionService presentacionService;

    /** La foto de la presentacion; si falta el tamano pedido, el siguiente mas grande que exista. */
    public String dePresentacion(Long presentacionId, Tamano tamano) {
        if (presentacionId == null) {
            return null;
        }
        try {
            String nombre = presentacionId + ".jpg";
            String foto = null;
            if (tamano == Tamano.MINIATURA) {
                foto = leer(new File(imageService.getImagePresentacionesThumb() + nombre));
            }
            if (foto == null && tamano != Tamano.ORIGINAL) {
                foto = leer(new File(imageService.getImagePresentacionesMediana() + nombre));
            }
            if (foto == null) {
                foto = leer(new File(imageService.getImagePresentaciones() + nombre));
            }
            return foto != null ? foto : deImagenMaster(TipoReferencia.PRESENTACION, presentacionId);
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la foto de la presentacion {}: {}", presentacionId, e.getMessage());
            return null;
        }
    }

    /** La foto de la presentacion principal del producto; sin principal, la que el producto tenga propia. */
    public String deProducto(Long productoId, Tamano tamano) {
        if (productoId == null) {
            return null;
        }
        try {
            Presentacion principal = presentacionService.findByPrincipalAndProductoId(true, productoId);
            if (principal != null) {
                return dePresentacion(principal.getId(), tamano);
            }
            String foto = deImagenMaster(TipoReferencia.PRODUCTO, productoId);
            return foto != null ? foto : leer(new File(imageService.getImagePath() + "producto_" + productoId + ".jpg"));
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la foto del producto {}: {}", productoId, e.getMessage());
            return null;
        }
    }

    /** La foto de la presentacion vencida y, si no tiene, la del producto. */
    public String deVencimiento(Long presentacionId, Long productoId, Tamano tamano) {
        String foto = dePresentacion(presentacionId, tamano);
        return foto != null ? foto : deProducto(productoId, tamano);
    }

    /**
     * Respaldo para fotos que solo quedaron en {@code imagen_master}. Puede haber varias filas por
     * referencia, o filas cuyo archivo ya no esta: se toma la primera que se pueda leer, con la
     * marcada como principal por delante.
     */
    private String deImagenMaster(TipoReferencia tipo, Long referenciaId) {
        List<ImagenMaster> filas = imagenMasterRepository.findByTipoReferenciaAndReferenciaId(tipo, referenciaId);
        String otra = null;
        for (ImagenMaster fila : filas) {
            if (fila.getUrl() == null) {
                continue;
            }
            String foto = leer(new File(fila.getUrl()));
            if (foto == null) {
                continue;
            }
            if (Boolean.TRUE.equals(fila.getPrincipal())) {
                return foto;
            }
            if (otra == null) {
                otra = foto;
            }
        }
        return otra;
    }

    private String leer(File archivo) {
        return archivo.isFile() ? imageService.fileToBase64(archivo) : null;
    }
}
