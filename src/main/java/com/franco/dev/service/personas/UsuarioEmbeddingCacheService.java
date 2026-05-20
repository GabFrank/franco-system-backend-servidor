package com.franco.dev.service.personas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Caché en memoria de embeddings faciales de usuarios activos.
 * Evita cargar y parsear JSON en cada búsqueda por similitud.
 */
@Service
@Slf4j
public class UsuarioEmbeddingCacheService {

    private static final double MATCH_THRESHOLD = 0.75;

    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<CachedEntry> entries = new CopyOnWriteArrayList<>();

    public UsuarioEmbeddingCacheService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = new ObjectMapper();
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
        log.info("Caché de embeddings faciales: {} usuarios activos con embedding", entries.size());
    }

    public void refreshUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        entries.removeIf(e -> e.usuarioId.equals(usuarioId));
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            if (Boolean.TRUE.equals(usuario.getActivo())) {
                parseEntry(usuario).ifPresent(entries::add);
            }
        });
    }

    public UsuarioSimilitudResult findBestMatch(List<Double> queryEmbedding, List<Integer> excludeIds) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || entries.isEmpty()) {
            return null;
        }

        double[] query = toArray(queryEmbedding);
        List<Integer> excluded = excludeIds != null ? excludeIds : Collections.emptyList();

        CachedEntry best = null;
        double maxSimilarity = -1.0;

        for (CachedEntry entry : entries) {
            if (excluded.contains(entry.usuarioId.intValue())) {
                continue;
            }
            double similarity = cosineSimilarity(query, entry.embedding);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                best = entry;
            }
        }

        if (best != null && maxSimilarity > MATCH_THRESHOLD) {
            return new UsuarioSimilitudResult(best.usuario, maxSimilarity);
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
        if (json == null || json.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {
            });
            double[] arr = toArray(list);
            if (arr.length == 0) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new CachedEntry(usuario.getId(), arr, usuario));
        } catch (Exception e) {
            log.warn("Embedding inválido para usuario {}: {}", usuario.getId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private static double[] toArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i) != null ? list.get(i) : 0.0;
        }
        return arr;
    }

    private static double cosineSimilarity(double[] v1, double[] v2) {
        if (v1.length != v2.length || v1.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static final class CachedEntry {
        private final Long usuarioId;
        private final double[] embedding;
        private final Usuario usuario;

        private CachedEntry(Long usuarioId, double[] embedding, Usuario usuario) {
            this.usuarioId = usuarioId;
            this.embedding = embedding;
            this.usuario = usuario;
        }
    }
}
