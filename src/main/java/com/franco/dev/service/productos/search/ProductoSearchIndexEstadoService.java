package com.franco.dev.service.productos.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Detecta si el índice Lucene de productos está vacío o no existe.
 */
@Service
public class ProductoSearchIndexEstadoService {

    @Value("${spring.jpa.properties.hibernate.search.backend.directory.root:./data/lucene/productos}")
    private String luceneDirectory;

    public boolean indiceVacioONoExiste() {
        Path path = Paths.get(luceneDirectory);
        if (!Files.isDirectory(path)) {
            return true;
        }
        try {
            return Files.list(path)
                    .noneMatch(p -> p.getFileName().toString().startsWith("segments"));
        } catch (IOException e) {
            return true;
        }
    }
}
