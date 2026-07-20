package com.franco.dev.controller.activos;

import com.franco.dev.service.activos.EnteArchivoDocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/api/activos/documentos")
@CrossOrigin
public class EnteArchivoDocumentController {

    private final EnteArchivoDocumentService documentService;

    public EnteArchivoDocumentController(EnteArchivoDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/download")
    public ResponseEntity<?> descargarPorNombre(@RequestParam("nombre") String fileName) {
        return responderArchivo(fileName);
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<?> descargar(@PathVariable String fileName) {
        return responderArchivo(fileName);
    }

    private ResponseEntity<?> responderArchivo(String fileName) {
        File archivo = documentService.resolverArchivo(fileName);
        if (archivo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Archivo no encontrado");
        }

        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (lower.endsWith(".png")) {
            contentType = MediaType.IMAGE_PNG_VALUE;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        } else if (lower.endsWith(".gif")) {
            contentType = "image/gif";
        } else if (lower.endsWith(".webp")) {
            contentType = "image/webp";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.getName() + "\"")
                .body(new FileSystemResource(archivo));
    }
}
