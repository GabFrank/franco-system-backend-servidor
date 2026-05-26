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

/**
 * Convierte la primera pagina de un PDF a JPEG para enviar a la API Vision de OpenAI.
 * OpenAI gpt-4o no acepta PDFs directamente; necesita imagenes.
 */
@Service
public class PdfConverterService {

    private static final Logger log = LoggerFactory.getLogger(PdfConverterService.class);

    private static final int DPI = 150;
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * @param pdfBytes contenido binario de un PDF
     * @return JPEG (bytes) renderizando la primera pagina a 150 DPI
     * @throws IOException si el PDF es invalido o tiene 0 paginas
     */
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
