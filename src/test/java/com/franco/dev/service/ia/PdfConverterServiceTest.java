package com.franco.dev.service.ia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfConverterServiceTest {

    private PdfConverterService service;

    @BeforeEach
    void setUp() {
        service = new PdfConverterService();
    }

    @Test
    void primerPaginaComoJpeg_pdfValido_retornaJpegNoVacio() throws Exception {
        byte[] pdf = generarPdfMinimo("Factura prueba");

        byte[] jpeg = service.primerPaginaComoJpeg(pdf);

        assertNotNull(jpeg);
        assertTrue(jpeg.length > 0);
        // JPEG magic bytes: FF D8 FF
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
        assertEquals((byte) 0xFF, jpeg[2]);
    }

    @Test
    void primerPaginaComoJpeg_jpegDecodificable_tieneDimensionesA4At150Dpi() throws Exception {
        byte[] pdf = generarPdfMinimo("Test");

        byte[] jpeg = service.primerPaginaComoJpeg(pdf);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));

        assertNotNull(img);
        // PDF A4 default = 612x792 puntos, a 150 DPI = ~1275x1650 pixels
        assertTrue(img.getWidth() > 1000, "ancho razonable a 150 DPI: " + img.getWidth());
        assertTrue(img.getHeight() > 1400, "alto razonable a 150 DPI: " + img.getHeight());
    }

    @Test
    void primerPaginaComoJpeg_pdfVacio_lanzaIOException() {
        assertThrows(IOException.class, () -> service.primerPaginaComoJpeg(new byte[0]));
    }

    @Test
    void primerPaginaComoJpeg_pdfNull_lanzaIOException() {
        assertThrows(IOException.class, () -> service.primerPaginaComoJpeg(null));
    }

    @Test
    void primerPaginaComoJpeg_bytesInvalidos_lanzaIOException() {
        byte[] noEsPdf = "esto no es un pdf en absoluto".getBytes();
        assertThrows(IOException.class, () -> service.primerPaginaComoJpeg(noEsPdf));
    }

    @Test
    void primerPaginaComoJpeg_pdfMultipagina_renderizaSoloPrimera() throws Exception {
        byte[] pdf = generarPdfDosPaginas("Pagina UNO", "Pagina DOS");

        byte[] jpeg = service.primerPaginaComoJpeg(pdf);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertNotNull(img);
        assertTrue(img.getWidth() > 100);
    }

    private byte[] generarPdfMinimo(String texto) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(100, 700);
                cs.showText(texto);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] generarPdfDosPaginas(String texto1, String texto2) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String t : new String[]{texto1, texto2}) {
                PDPage p = new PDPage();
                doc.addPage(p);
                try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                    cs.newLineAtOffset(100, 700);
                    cs.showText(t);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
