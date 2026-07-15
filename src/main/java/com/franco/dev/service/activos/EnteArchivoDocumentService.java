package com.franco.dev.service.activos;

import com.franco.dev.service.utils.ImageService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Service
public class EnteArchivoDocumentService {

    private static final String SUBCARPETA = "activos" + File.separator + "documentos";

    private final ImageService imageService;

    public EnteArchivoDocumentService(ImageService imageService) {
        this.imageService = imageService;
    }

    public String getDocumentosPath() {
        return imageService.getImagePath() + SUBCARPETA + File.separator;
    }

    public String guardar(Long enteId, String tipoArchivo, String nombreOriginal, String contenidoBase64)
            throws IOException {
        if (enteId == null) {
            throw new IllegalArgumentException("enteId es requerido");
        }
        if (contenidoBase64 == null || contenidoBase64.isBlank()) {
            throw new IllegalArgumentException("contenido del archivo es requerido");
        }

        String extension = extraerExtension(nombreOriginal);
        String tipo = tipoArchivo != null ? tipoArchivo : "OTRO";
        String fileName = enteId + "_" + tipo + "_" + System.currentTimeMillis() + "." + extension;
        String dirPath = getDocumentosPath();
        File directorio = new File(dirPath);
        if (!directorio.exists() && !directorio.mkdirs()) {
            throw new IOException("No se pudo crear el directorio de documentos");
        }

        byte[] bytes = decodificarBase64(contenidoBase64);
        Path destino = Paths.get(dirPath + fileName);
        Files.write(destino, bytes);
        return fileName;
    }

    public List<String> getContenidoBase64(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Collections.emptyList();
        }
        File archivo = resolverArchivo(fileName);
        if (archivo == null) {
            return Collections.emptyList();
        }
        try {
            String contenido = imageService.fileToBase64(archivo);
            if (contenido == null || contenido.isBlank()) {
                return Collections.emptyList();
            }
            return List.of(contenido);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public File resolverArchivo(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String nombreSeguro = Paths.get(fileName).getFileName().toString();
        if (nombreSeguro.contains("..")) {
            return null;
        }
        File archivo = new File(getDocumentosPath() + nombreSeguro);
        return archivo.exists() ? archivo : null;
    }

    private byte[] decodificarBase64(String contenido) {
        String payload = contenido;
        if (contenido.contains(",")) {
            payload = contenido.substring(contenido.indexOf(',') + 1);
        }
        return Base64.getDecoder().decode(payload);
    }

    private String extraerExtension(String nombreOriginal) {
        if (nombreOriginal == null || !nombreOriginal.contains(".")) {
            return "bin";
        }
        String ext = nombreOriginal.substring(nombreOriginal.lastIndexOf('.') + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,10}") ? ext : "bin";
    }
}
