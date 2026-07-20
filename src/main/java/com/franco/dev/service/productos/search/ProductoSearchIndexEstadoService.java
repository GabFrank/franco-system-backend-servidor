package com.franco.dev.service.productos.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Detecta si los índices Lucene de productos y códigos están listos.
 * Usado para reindexación automática al arrancar, sin intervención manual.
 */
@Service
public class ProductoSearchIndexEstadoService {

    private static final String ENTIDAD_PRODUCTO = "Producto";
    private static final String ENTIDAD_CODIGO = "Codigo";

    @Value("${spring.jpa.properties.hibernate.search.backend.directory.root:./data/lucene/productos}")
    private String luceneDirectory;

    /**
     * @deprecated Usar {@link #requiereReindexacionAutomatica()}.
     */
    @Deprecated
    public boolean indiceVacioONoExiste() {
        return requiereReindexacionAutomatica();
    }

    /**
     * Verdadero si falta el índice general, el de productos o el de códigos.
     */
    public boolean requiereReindexacionAutomatica() {
        Path root = Paths.get(luceneDirectory);
        if (!Files.isDirectory(root)) {
            return true;
        }
        if (!indiceEntidadPresente(root, ENTIDAD_PRODUCTO)) {
            return true;
        }
        return !indiceEntidadPresente(root, ENTIDAD_CODIGO);
    }

    /**
     * Verdadero cuando productos ya están indexados pero códigos aún no
     * (por ejemplo, tras agregar el índice de códigos en una versión nueva).
     */
    public boolean requiereReindexacionSoloCodigos() {
        Path root = Paths.get(luceneDirectory);
        if (!Files.isDirectory(root)) {
            return false;
        }
        return indiceEntidadPresente(root, ENTIDAD_PRODUCTO)
                && !indiceEntidadPresente(root, ENTIDAD_CODIGO);
    }

    public boolean indiceProductoPresente() {
        Path root = Paths.get(luceneDirectory);
        return Files.isDirectory(root) && indiceEntidadPresente(root, ENTIDAD_PRODUCTO);
    }

    public boolean indiceCodigoPresente() {
        Path root = Paths.get(luceneDirectory);
        return Files.isDirectory(root) && indiceEntidadPresente(root, ENTIDAD_CODIGO);
    }

    private boolean indiceEntidadPresente(Path root, String nombreEntidad) {
        try (Stream<Path> directorios = Files.walk(root, 4)) {
            return directorios
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().contains(nombreEntidad))
                    .anyMatch(this::directorioTieneSegmentos);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean directorioTieneSegmentos(Path directorio) {
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos.anyMatch(path -> path.getFileName().toString().startsWith("segments"));
        } catch (IOException e) {
            return false;
        }
    }
}
