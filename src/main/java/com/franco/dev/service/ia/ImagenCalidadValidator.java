package com.franco.dev.service.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Valida que una imagen subida tenga resolucion suficiente para que OpenAI Vision lea bien
 * facturas con tablas densas. Aprendido en el smoke test: las imagenes reenviadas por WhatsApp
 * (~1080x1480) hacen que el modelo repita items en loop y trunque la respuesta; los escaneos /
 * fotos originales (>=2000px) se leen bien. No aplica a paginas renderizadas de un PDF (esas las
 * generamos nosotros a DPI controlado).
 */
@Service
public class ImagenCalidadValidator {

    private static final Logger log = LoggerFactory.getLogger(ImagenCalidadValidator.class);

    /** Lado mayor minimo en pixeles. Las imagenes de WhatsApp (lado mayor ~1500) quedan debajo. */
    static final int MIN_LADO_MAYOR_PX = 1600;

    /**
     * @throws IllegalArgumentException si la imagen no se puede leer o es de baja resolucion
     */
    public void validar(byte[] bytes, String nombre) {
        String n = nombre != null ? nombre : "imagen";
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer la imagen '" + n + "': " + e.getMessage());
        }
        if (img == null) {
            throw new IllegalArgumentException("Formato de imagen no soportado o archivo corrupto: '" + n + "'");
        }
        int ladoMayor = Math.max(img.getWidth(), img.getHeight());
        if (ladoMayor < MIN_LADO_MAYOR_PX) {
            throw new IllegalArgumentException("Imagen '" + n + "' de baja resolucion ("
                    + img.getWidth() + "x" + img.getHeight() + "). Subí el escaneo o la foto original "
                    + "(lado mayor >= " + MIN_LADO_MAYOR_PX + "px). Las imagenes reenviadas por WhatsApp "
                    + "suelen venir reducidas y la IA no logra leer los items.");
        }
        log.debug("Imagen '{}' OK: {}x{}", n, img.getWidth(), img.getHeight());
    }
}
