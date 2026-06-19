package com.franco.dev.service.ia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte las paginas de un PDF a JPEG para enviar a la API Vision de OpenAI.
 * OpenAI gpt-4o no acepta PDFs directamente; necesita imagenes.
 *
 * Una factura legal puede ocupar varias paginas (el listado de items continua en hojas
 * sucesivas y el total suele estar en la ultima). Por eso {@link #todasLasPaginasComoJpeg}
 * renderiza TODAS las paginas: enviar solo la primera perderia items silenciosamente.
 */
@Service
public class PdfConverterService {

    private static final Logger log = LoggerFactory.getLogger(PdfConverterService.class);

    private static final int DPI = 150;
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Tope de paginas a renderizar. Cada pagina es una imagen extra enviada a Vision
     * (mas tokens, mas costo). Si un PDF supera el tope se rechaza con error explicito
     * en lugar de perder paginas en silencio.
     */
    private static final int MAX_PAGINAS = 15;

    /**
     * Renderiza TODAS las paginas del PDF a JPEG, en orden.
     *
     * @param pdfBytes contenido binario de un PDF
     * @return lista de JPEG (bytes), uno por pagina, a 150 DPI
     * @throws IOException si el PDF es invalido, tiene 0 paginas o supera {@link #MAX_PAGINAS}
     */
    public List<byte[]> todasLasPaginasComoJpeg(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IOException("PDF vacio");
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw new IOException("PDF sin paginas");
            }
            if (pages > MAX_PAGINAS) {
                throw new IOException("El PDF tiene " + pages + " paginas (maximo " + MAX_PAGINAS
                        + "). Divida el documento en archivos mas chicos.");
            }
            log.debug("PDF cargado, {} paginas, renderizando todas a {} DPI", pages, DPI);
            PDFRenderer renderer = new PDFRenderer(doc);
            List<byte[]> jpegs = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "jpg", out);
                jpegs.add(out.toByteArray());
                log.debug("PDF pagina {}/{} -> JPEG {} bytes ({}x{})",
                        i + 1, pages, jpegs.get(i).length, image.getWidth(), image.getHeight());
            }
            return jpegs;
        }
    }

    /**
     * @param pdfBytes contenido binario de un PDF
     * @return JPEG (bytes) renderizando solo la primera pagina a 150 DPI
     * @throws IOException si el PDF es invalido o tiene 0 paginas
     * @deprecated usar {@link #todasLasPaginasComoJpeg} — la primera pagina sola pierde items
     *             en facturas de varias hojas. Se mantiene por compatibilidad.
     */
    @Deprecated
    public byte[] primerPaginaComoJpeg(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IOException("PDF vacio");
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw new IOException("PDF sin paginas");
            }
            log.debug("PDF cargado, {} paginas, renderizando pagina 1 a {} DPI", pages, DPI);
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(0, DPI, ImageType.RGB);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            byte[] jpeg = out.toByteArray();
            log.debug("JPEG generado: {} bytes, dimensiones {}x{}", jpeg.length, image.getWidth(), image.getHeight());
            return jpeg;
        }
    }
}
