package com.franco.dev.service.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El logo de los reportes sale del disco del servidor: un host sin logo.png, con el archivo vacio o
 * roto tiene que imprimir el recibo igual, sin logo. Y el que se embebe va escalado, no el original.
 */
class ImageServiceLogoReporteTest {

    @TempDir
    Path dir;

    private final ImageService service = new ImageService();

    @Test
    void sinArchivoDevuelveNull() {
        assertNull(service.cargarLogoReporte(dir.resolve("logo.png").toFile()));
    }

    @Test
    void archivoVacioDevuelveNull() throws Exception {
        File vacio = Files.createFile(dir.resolve("logo.png")).toFile();
        assertNull(service.cargarLogoReporte(vacio));
    }

    @Test
    void archivoQueNoEsImagenDevuelveNull() throws Exception {
        File roto = Files.write(dir.resolve("logo.png"), "no soy un png".getBytes(StandardCharsets.UTF_8)).toFile();
        assertNull(service.cargarLogoReporte(roto));
    }

    @Test
    void elLogoGrandeSeEscalaAlAnchoDeReporteSinDeformar() throws Exception {
        // Mismo tamaño que el logo.png real (1701x1028).
        File logo = png(1701, 1028);

        BufferedImage img = service.cargarLogoReporte(logo);

        assertNotNull(img);
        assertEquals(ImageService.ANCHO_LOGO_REPORTE, img.getWidth());
        assertEquals(1028.0 * ImageService.ANCHO_LOGO_REPORTE / 1701, img.getHeight(), 1.0);
    }

    @Test
    void unLogoChicoNoSeAgranda() throws Exception {
        BufferedImage img = service.cargarLogoReporte(png(200, 120));

        assertEquals(200, img.getWidth());
    }

    @Test
    void seCacheaHastaQueCambiaElArchivo() throws Exception {
        File logo = png(800, 400);

        BufferedImage primera = service.cargarLogoReporte(logo);
        assertSame(primera, service.cargarLogoReporte(logo), "sin cambios en disco no se vuelve a decodificar");

        ImageIO.write(new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", logo);
        assertTrue(logo.setLastModified(logo.lastModified() + 5000));
        BufferedImage nueva = service.cargarLogoReporte(logo);
        assertNotSame(primera, nueva, "un logo nuevo en disco se toma sin reiniciar");
        assertEquals(ImageService.ANCHO_LOGO_REPORTE, nueva.getWidth());
    }

    private File png(int ancho, int alto) throws Exception {
        File f = dir.resolve("logo.png").toFile();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", f);
        return f;
    }
}
