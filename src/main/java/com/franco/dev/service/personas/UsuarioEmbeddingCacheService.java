package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.UsuarioSimilitudResult;
import com.franco.dev.repository.personas.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Caché en memoria de galerías faciales de usuarios activos.
 */
@Service
@Slf4j
public class UsuarioEmbeddingCacheService {

    private static final double MATCH_THRESHOLD = 0.55;

    private final UsuarioRepository usuarioRepository;
    private final EmbeddingGaleriaService embeddingGaleriaService;
    private final CopyOnWriteArrayList<CachedEntry> entries = new CopyOnWriteArrayList<>();

    public UsuarioEmbeddingCacheService(
            UsuarioRepository usuarioRepository,
            EmbeddingGaleriaService embeddingGaleriaService) {
        this.usuarioRepository = usuarioRepository;
        this.embeddingGaleriaService = embeddingGaleriaService;
    }

    @PostConstruct
    public void warmUp() {
        refreshAll();
    }

    public void refreshAll() {
        List<Usuario> usuarios = usuarioRepository.findActivosConEmbedding();
        List<CachedEntry> loaded = new ArrayList<>();
        for (Usuario usuario : usuarios) {
            parseEntry(usuario).ifPresent(loaded::add);
        }
        entries.clear();
        entries.addAll(loaded);
        log.info("Caché de embeddings faciales: {} usuarios activos con galería", entries.size());
    }

    public void refreshUsuario(Long usuarioId, String embeddingJson) {
        if (usuarioId == null) {
            return;
        }
        entries.removeIf(e -> e.usuarioId.equals(usuarioId));

        if (embeddingJson != null && !embeddingJson.isBlank()) {
            com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto galeria = embeddingGaleriaService
                    .parsearDesdeJson(embeddingJson);
            if (galeria == null || galeria.getGallery() == null || galeria.getGallery().isEmpty()) {
                return;
            }
            List<double[]> vectores = embeddingGaleriaService.extraerVectores(galeria);
            if (!vectores.isEmpty()) {
                usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
                    if (Boolean.TRUE.equals(usuario.getActivo())) {
                        entries.add(new CachedEntry(usuarioId, vectores, usuario));
                        log.debug("Caché de galería facial actualizada para usuario {}", usuarioId);
                    }
                });
            }
            return;
        }

        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            if (!Boolean.TRUE.equals(usuario.getActivo())) {
                return;
            }
            parseEntry(usuario).ifPresent(entries::add);
        });
    }

    public UsuarioSimilitudResult findBestMatch(List<Double> queryEmbedding, List<Integer> excludeIds) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || entries.isEmpty()) {
            return null;
        }

        List<Integer> excluded = excludeIds != null ? excludeIds : Collections.emptyList();

        CachedEntry best = null;
        double maxSimilarity = -1.0;
        // El segundo mejor no es un extra: es lo unico que dice si el primero gano por
        // mucho o por nada. Sin esto, 0,71 contra 0,45 y 0,71 contra 0,69 son la misma
        // respuesta, y la segunda es una moneda al aire.
        Double segundaSimilarity = null;

        for (CachedEntry entry : entries) {
            if (excluded.contains(entry.usuarioId.intValue())) {
                continue;
            }
            double similarity = embeddingGaleriaService.calcularMaximaSimilitud(queryEmbedding, entry.vectores);
            if (similarity > maxSimilarity) {
                // El que venia primero pasa a segundo. Sin el `best != null` esto
                // registraria como segundo el -1 del arranque, que no es nadie.
                if (best != null) {
                    segundaSimilarity = maxSimilarity;
                }
                maxSimilarity = similarity;
                best = entry;
            } else if (segundaSimilarity == null || similarity > segundaSimilarity) {
                segundaSimilarity = similarity;
            }
        }

        if (best != null) {
            // Con un solo candidato no hay margen. Devolver 0 o 1 seria inventar una
            // medicion: null dice "no habia contra quien comparar", que es distinto.
            Double margen = segundaSimilarity != null ? maxSimilarity - segundaSimilarity : null;
            return new UsuarioSimilitudResult(best.usuario, maxSimilarity, segundaSimilarity, margen);
        }
        return null;
    }

    public int size() {
        return entries.size();
    }

    private java.util.Optional<CachedEntry> parseEntry(Usuario usuario) {
        if (usuario == null || usuario.getPersona() == null) {
            return java.util.Optional.empty();
        }
        String json = usuario.getPersona().getEmbedding();
        com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto galeria = embeddingGaleriaService.parsearDesdeJson(json);
        if (galeria == null || galeria.getGallery() == null || galeria.getGallery().isEmpty()) {
            return java.util.Optional.empty();
        }
        List<double[]> vectores = embeddingGaleriaService.extraerVectores(galeria);
        if (vectores.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new CachedEntry(usuario.getId(), vectores, usuario));
    }

    private static final class CachedEntry {
        private final Long usuarioId;
        private final List<double[]> vectores;
        private final Usuario usuario;

        private CachedEntry(Long usuarioId, List<double[]> vectores, Usuario usuario) {
            this.usuarioId = usuarioId;
            this.vectores = vectores;
            this.usuario = usuario;
        }
    }
}
