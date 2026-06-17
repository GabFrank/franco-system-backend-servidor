package com.franco.dev.service.personas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaItemDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmbeddingGaleriaService {

    private final ObjectMapper objectMapper;

    public EmbeddingGaleriaService() {
        this.objectMapper = new ObjectMapper();
    }

    public EmbeddingGaleriaDto parsearDesdeJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isObject()) {
                EmbeddingGaleriaDto galeria = objectMapper.treeToValue(root, EmbeddingGaleriaDto.class);
                return normalizar(galeria);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public String serializar(EmbeddingGaleriaDto galeria) {
        if (galeria == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizar(galeria));
        } catch (Exception e) {
            return null;
        }
    }

    public String normalizarJsonEntrada(String embeddingGaleriaJson) {
        EmbeddingGaleriaDto galeria = parsearDesdeJson(embeddingGaleriaJson);
        if (galeria == null || galeria.getMaster() == null || galeria.getMaster().isEmpty()) {
            throw new IllegalArgumentException("embeddingGaleriaJson inválido o sin master");
        }
        if (galeria.getGallery() == null || galeria.getGallery().isEmpty()) {
            throw new IllegalArgumentException("embeddingGaleriaJson debe incluir al menos un item en gallery");
        }
        return serializar(galeria);
    }

    public List<double[]> extraerVectores(EmbeddingGaleriaDto galeria) {
        if (galeria == null) {
            return Collections.emptyList();
        }
        List<double[]> vectores = new ArrayList<>();
        double[] master = toArray(galeria.getMaster());
        if (master.length > 0) {
            vectores.add(master);
        }
        if (galeria.getGallery() != null) {
            for (EmbeddingGaleriaItemDto item : galeria.getGallery()) {
                if (item == null || item.getEmbedding() == null) {
                    continue;
                }
                double[] vector = toArray(item.getEmbedding());
                if (vector.length > 0) {
                    vectores.add(vector);
                }
            }
        }
        return vectores;
    }

    public List<double[]> extraerVectoresDesdeJson(String json) {
        return extraerVectores(parsearDesdeJson(json));
    }

    public double calcularMaximaSimilitud(List<Double> consulta, List<double[]> referencias) {
        if (consulta == null || consulta.isEmpty() || referencias == null || referencias.isEmpty()) {
            return 0.0;
        }
        double[] query = toArray(consulta);
        double maxima = -1.0;
        for (double[] referencia : referencias) {
            double similitud = cosineSimilarity(query, referencia);
            if (similitud > maxima) {
                maxima = similitud;
            }
        }
        return maxima < 0 ? 0.0 : maxima;
    }

    private EmbeddingGaleriaDto normalizar(EmbeddingGaleriaDto galeria) {
        if (galeria.getMaster() == null) {
            galeria.setMaster(new ArrayList<>());
        }
        if (galeria.getGallery() == null) {
            galeria.setGallery(new ArrayList<>());
        }
        return galeria;
    }

    private static double[] toArray(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return new double[0];
        }
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
}
