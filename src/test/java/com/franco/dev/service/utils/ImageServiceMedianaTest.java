package com.franco.dev.service.utils;

import com.franco.dev.service.utils.ImageService.ResultadoMediana;
import com.franco.dev.service.utils.ImageService.ResumenMedianas;
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
 * La imagen mediana es lo que piden las vistas grandes para no bajar el original (issue #263): hasta
 * 800 px de lado mayor, sin agrandar las fotos chicas, y siempre de la foto vigente.
 */
class ImageServiceMedianaTest {

    @TempDir
    Path dir;

    private final ImageService service = new ImageService();

    @Test
    void laFotoGrandeSeReduceAlLadoMayorSinDeformar() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();

        assertEquals(ResultadoMediana.GENERADA, service.guardarMediana(foto("1.jpg", 2000, 1000, false), destino));

        BufferedImage mediana = ImageIO.read(destino);
        assertEquals(800, mediana.getWidth());
        assertEquals(400, mediana.getHeight());
    }

    @Test
    void laFotoVerticalSeReducePorSuAlto() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();

        service.guardarMediana(foto("1.jpg", 1000, 2000, false), destino);

        BufferedImage mediana = ImageIO.read(destino);
        assertEquals(400, mediana.getWidth());
        assertEquals(800, mediana.getHeight());
    }

    @Test
    void laFotoConTransparenciaSeGuardaIgual() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();

        assertEquals(ResultadoMediana.GENERADA, service.guardarMediana(foto("1.png", 1600, 1600, true), destino));
        assertEquals(800, ImageIO.read(destino).getWidth());
    }

    @Test
    void laFotoChicaNoGeneraArchivo() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();

        assertEquals(ResultadoMediana.NO_HACE_FALTA, service.guardarMediana(foto("1.jpg", 600, 600, false), destino));
        assertFalse(destino.exists());
    }

    @Test
    void reemplazarPorUnaFotoChicaBorraLaMedianaAnterior() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();
        service.guardarMediana(foto("1.jpg", 2000, 1000, false), destino);
        assertTrue(destino.exists());

        service.guardarMediana(foto("1.jpg", 600, 600, false), destino);

        assertFalse(destino.exists(), "quedaria sirviendo la foto anterior");
    }

    @Test
    void reemplazarPorOtraFotoGrandeRegeneraLaMediana() throws Exception {
        File destino = dir.resolve("medianas/1.jpg").toFile();
        service.guardarMediana(foto("1.jpg", 2000, 1000, false), destino);

        service.guardarMediana(foto("1.jpg", 1000, 2000, false), destino);

        assertEquals(400, ImageIO.read(destino).getWidth());
    }

    @Test
    void unArchivoQueNoEsImagenFallaSinRomper() throws Exception {
        File roto = Files.write(dir.resolve("1.jpg"), "no soy una foto".getBytes(StandardCharsets.UTF_8)).toFile();
        File destino = dir.resolve("medianas/1.jpg").toFile();

        assertEquals(ResultadoMediana.FALLIDA, service.guardarMediana(roto, destino));
        assertFalse(destino.exists());
    }

    @Test
    void laPasadaGeneraLasFaltantesYRepetirlaNoReescribe() throws Exception {
        foto("1.jpg", 2000, 1000, false);
        foto("2.jpg", 600, 600, false);
        Files.write(dir.resolve("3.jpg"), "rota".getBytes(StandardCharsets.UTF_8));
        foto("logo.jpg", 2000, 1000, false); // no es una foto de presentacion
        File medianas = dir.resolve("medianas").toFile();

        ResumenMedianas primera = service.generarMedianasFaltantes(dir.toFile(), medianas);

        assertEquals(1, primera.getGeneradas());
        assertEquals(1, primera.getSalteadas());
        assertEquals(1, primera.getFallidas());
        File generada = new File(medianas, "1.jpg");
        assertTrue(generada.isFile());
        assertFalse(new File(medianas, "logo.jpg").exists());

        long escrita = generada.lastModified();
        ResumenMedianas segunda = service.generarMedianasFaltantes(dir.toFile(), medianas);

        assertEquals(0, segunda.getGeneradas());
        assertEquals(escrita, generada.lastModified());
    }

    @Test
    void laPasadaRegeneraLaMedianaDeUnaFotoReemplazadaDespues() throws Exception {
        File original = foto("1.jpg", 2000, 1000, false);
        File medianas = dir.resolve("medianas").toFile();
        service.generarMedianasFaltantes(dir.toFile(), medianas);
        File mediana = new File(medianas, "1.jpg");
        assertTrue(mediana.setLastModified(original.lastModified() - 60_000));

        assertEquals(1, service.generarMedianasFaltantes(dir.toFile(), medianas).getGeneradas());
    }

    @Test
    void sinDirectorioDeFotosNoHayNadaQueGenerar() {
        ResumenMedianas resumen = service.generarMedianasFaltantes(dir.resolve("no-existe").toFile(), dir.resolve("m").toFile());

        assertEquals(0, resumen.getGeneradas() + resumen.getSalteadas() + resumen.getFallidas());
    }

    private File foto(String nombre, int ancho, int alto, boolean transparente) throws Exception {
        File f = dir.resolve(nombre).toFile();
        BufferedImage img = new BufferedImage(ancho, alto,
                transparente ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, transparente ? "png" : "jpg", f);
        return f;
    }
}
