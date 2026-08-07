package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.FuncionarioDocumento;
import com.franco.dev.repository.rrhh.FuncionarioDocumentoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.utils.ImageService;
import graphql.GraphQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class FuncionarioDocumentoService
        extends CrudService<FuncionarioDocumento, FuncionarioDocumentoRepository, Long> {

    private static final String SUBCARPETA = "rrhh" + File.separator + "funcionario-documentos";

    private final FuncionarioDocumentoRepository repository;
    private final ImageService imageService;

    public FuncionarioDocumentoService(FuncionarioDocumentoRepository repository, ImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    @Override
    public FuncionarioDocumentoRepository getRepository() {
        return repository;
    }

    public List<FuncionarioDocumento> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdAndAnuladoFalseOrderByFechaSubidaDesc(funcionarioId);
    }

    private String getDirPath() {
        return imageService.getImagePath() + SUBCARPETA + File.separator;
    }

    /**
     * Persiste el binario base64 en disco y devuelve el nombre de archivo generado.
     * Crea el directorio si no existe (no asumir carpetas en el server).
     */
    public String guardarArchivo(Long funcionarioId, String tipo, String nombreOriginal, String contenidoBase64) {
        if (funcionarioId == null) throw new GraphQLException("funcionarioId es requerido");
        if (contenidoBase64 == null || contenidoBase64.isBlank())
            throw new GraphQLException("El contenido del archivo es requerido");

        String extension = extraerExtension(nombreOriginal);
        String t = tipo != null ? tipo : "OTRO";
        String fileName = funcionarioId + "_" + t + "_" + System.currentTimeMillis()
                + (extension.isEmpty() ? "" : "." + extension);
        String dirPath = getDirPath();
        File directorio = new File(dirPath);
        if (!directorio.exists() && !directorio.mkdirs())
            throw new GraphQLException("No se pudo crear el directorio de documentos");
        try {
            byte[] bytes = decodificarBase64(contenidoBase64);
            Path destino = Paths.get(dirPath + fileName);
            Files.write(destino, bytes);
        } catch (IOException ex) {
            throw new GraphQLException("Error al guardar el archivo: " + ex.getMessage());
        }
        return fileName;
    }

    /** Devuelve el contenido del documento como base64, o null si no existe. */
    public String getContenidoBase64(Long id) {
        Optional<FuncionarioDocumento> opt = repository.findById(id);
        if (opt.isEmpty()) return null;
        String nombre = opt.get().getNombreArchivo();
        if (nombre == null || nombre.isBlank()) return null;
        String nombreSeguro = Paths.get(nombre).getFileName().toString();
        if (nombreSeguro.contains("..")) return null;
        File archivo = new File(getDirPath() + nombreSeguro);
        if (!archivo.exists()) return null;
        return imageService.fileToBase64(archivo);
    }

    @Transactional
    public FuncionarioDocumento anular(Long id) {
        Optional<FuncionarioDocumento> opt = repository.findById(id);
        if (opt.isEmpty()) throw new GraphQLException("Documento no encontrado");
        FuncionarioDocumento d = opt.get();
        d.setAnulado(true);
        return repository.save(d);
    }

    @Override
    public FuncionarioDocumento save(FuncionarioDocumento entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getFechaSubida() == null) entity.setFechaSubida(LocalDateTime.now());
        if (entity.getAnulado() == null) entity.setAnulado(false);
        if (entity.getObservacion() != null) entity.setObservacion(entity.getObservacion().toUpperCase());
        return super.save(entity);
    }

    private String extraerExtension(String nombre) {
        if (nombre == null) return "";
        int dot = nombre.lastIndexOf('.');
        return (dot >= 0 && dot < nombre.length() - 1) ? nombre.substring(dot + 1) : "";
    }

    private byte[] decodificarBase64(String contenido) {
        String payload = contenido;
        if (contenido.contains(",")) payload = contenido.substring(contenido.indexOf(',') + 1);
        return Base64.getDecoder().decode(payload);
    }
}
