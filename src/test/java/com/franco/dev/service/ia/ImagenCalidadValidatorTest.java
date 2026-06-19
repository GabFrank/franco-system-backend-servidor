package com.franco.dev.service.ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImagenCalidadValidatorTest {

    private ImagenCalidadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImagenCalidadValidator();
    }

    @Test
    void aceptaImagenDeAltaResolucion() throws Exception {
        byte[] jpeg = jpeg(2000, 2600); // escaneo tipico
        assertDoesNotThrow(() -> validator.validar(jpeg, "scan.jpg"));
    }

    @Test
    void rechazaImagenDeBajaResolucion_tipoWhatsApp() throws Exception {
        byte[] jpeg = jpeg(1080, 1480); // imagen reducida por WhatsApp
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validar(jpeg, "wa.jpg"));
        assertTrue(ex.getMessage().toLowerCase().contains("resolucion"));
        assertTrue(ex.getMessage().contains("1080x1480"));
    }

    @Test
    void aceptaJustoEnElLimite() throws Exception {
        byte[] jpeg = jpeg(1200, ImagenCalidadValidator.MIN_LADO_MAYOR_PX);
        assertDoesNotThrow(() -> validator.validar(jpeg, "limite.jpg"));
    }

    @Test
    void rechazaBytesNoImagen() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validar("esto no es una imagen".getBytes(), "x.txt"));
    }

    private byte[] jpeg(int w, int h) throws Exception {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bi, "jpg", out);
        return out.toByteArray();
    }
}
