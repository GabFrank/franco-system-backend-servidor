package com.franco.dev.controller.print;

import com.franco.dev.service.print.ConstanciaRecepcionPrintService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controlador REST para generar y descargar constancias de recepción en PDF
 */
@RestController
@RequestMapping("/api/print/constancia-recepcion")
@AllArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ConstanciaRecepcionPrintController {

    private final ConstanciaRecepcionPrintService constanciaRecepcionPrintService;

    /**
     * Genera y descarga PDF de constancia de recepción
     */
    @GetMapping("/{recepcionId}/pdf")
    public ResponseEntity<byte[]> descargarConstanciaRecepcionPDF(@PathVariable Long recepcionId) {
        try {
            log.info("Generando PDF de constancia de recepción para ID: {}", recepcionId);
            
            // Generar PDF
            byte[] pdfBytes = constanciaRecepcionPrintService.generarConstanciaRecepcionPDF(recepcionId);
            
            // Nombre: {id-recepcion}-constancia-recepcion-{dd-MM-yy}.pdf
            String fechaStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"));
            String nombreArchivo = recepcionId + "-constancia-recepcion-" + fechaStr + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", nombreArchivo);
            headers.setContentLength(pdfBytes.length);
            
            log.info("PDF generado exitosamente. Tamaño: {} bytes", pdfBytes.length);
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("Error generando PDF de constancia de recepción", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error generando PDF: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Genera PDF de constancia de recepción y retorna como base64 (para preview)
     */
    @GetMapping("/{recepcionId}/pdf-base64")
    public ResponseEntity<String> obtenerConstanciaRecepcionBase64(@PathVariable Long recepcionId) {
        try {
            log.info("Generando PDF base64 de constancia de recepción para ID: {}", recepcionId);
            
            // Generar PDF
            byte[] pdfBytes = constanciaRecepcionPrintService.generarConstanciaRecepcionPDF(recepcionId);
            
            // Convertir a base64
            String base64 = java.util.Base64.getEncoder().encodeToString(pdfBytes);
            
            log.info("PDF base64 generado exitosamente. Tamaño: {} bytes", pdfBytes.length);
            
            return ResponseEntity.ok(base64);
            
        } catch (Exception e) {
            log.error("Error generando PDF base64 de constancia de recepción", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generando PDF: " + e.getMessage());
        }
    }
} 