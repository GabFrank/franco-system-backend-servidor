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

    private static final double MIN_SCORE_MARCACION = 0.7;
    private static final double MIN_SIMILITUD_MASTER = 0.55;
    private static final double MAX_SIMILITUD_DUPLICADO = 0.93;
    private static final int MAX_GALLERY_SIZE = 15;
    private static final double SCORE_MINIMO_MASTER = 0.5;
    private static final java.util.Set<String> POSES_ENROLLMENT = java.util.Set.of("left", "right", "front");

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

    /**
     * Incorpora un embedding de marcación verificada a la galería existente.
     * Solo agrega capturas de calidad que confirmen identidad y aporten diversidad visual.
     *
     * @return galería actualizada o null si la captura no cumple criterios de calidad
     */
    public EmbeddingGaleriaDto incorporarCapturaMarcacion(
            EmbeddingGaleriaDto galeria,
            List<Double> nuevoEmbedding,
            Double score) {
        if (galeria == null || nuevoEmbedding == null || nuevoEmbedding.isEmpty()) {
            return null;
        }
        double scoreCaptura = score != null ? score : 0.0;
        if (scoreCaptura < MIN_SCORE_MARCACION) {
            return null;
        }

        double[] candidato = toArray(nuevoEmbedding);
        double[] master = toArray(galeria.getMaster());
        if (master.length == 0 || candidato.length != master.length) {
            return null;
        }

        double similitudMaster = cosineSimilarity(candidato, master);
        if (similitudMaster < MIN_SIMILITUD_MASTER) {
            return null;
        }

        EmbeddingGaleriaDto actualizada = normalizar(galeria);
        if (actualizada.getGallery() == null) {
            actualizada.setGallery(new ArrayList<>());
        }

        int indiceDuplicado = -1;
        double maxSimilitudExistente = -1.0;
        for (int i = 0; i < actualizada.getGallery().size(); i++) {
            EmbeddingGaleriaItemDto item = actualizada.getGallery().get(i);
            if (item == null || item.getEmbedding() == null) {
                continue;
            }
            double similitud = cosineSimilarity(candidato, toArray(item.getEmbedding()));
            if (similitud > maxSimilitudExistente) {
                maxSimilitudExistente = similitud;
            }
            if (similitud >= MAX_SIMILITUD_DUPLICADO) {
                indiceDuplicado = i;
                break;
            }
        }

        if (indiceDuplicado >= 0) {
            EmbeddingGaleriaItemDto existente = actualizada.getGallery().get(indiceDuplicado);
            double scoreExistente = existente.getScore() != null ? existente.getScore() : 0.0;
            if (scoreCaptura <= scoreExistente) {
                return null;
            }
            existente.setEmbedding(new ArrayList<>(nuevoEmbedding));
            existente.setScore(scoreCaptura);
        } else {
            EmbeddingGaleriaItemDto item = new EmbeddingGaleriaItemDto();
            item.setPose("marcacion-" + System.currentTimeMillis());
            item.setEmbedding(new ArrayList<>(nuevoEmbedding));
            item.setScore(scoreCaptura);
            actualizada.getGallery().add(item);
            podarGaleria(actualizada);
        }

        actualizada.setMaster(recalcularMaster(actualizada.getGallery()));
        return actualizada;
    }

    private void podarGaleria(EmbeddingGaleriaDto galeria) {
        if (galeria.getGallery() == null || galeria.getGallery().size() <= MAX_GALLERY_SIZE) {
            return;
        }
        while (galeria.getGallery().size() > MAX_GALLERY_SIZE) {
            int indiceRemover = -1;
            double peorScore = Double.MAX_VALUE;
            for (int i = 0; i < galeria.getGallery().size(); i++) {
                EmbeddingGaleriaItemDto item = galeria.getGallery().get(i);
                if (item == null) {
                    indiceRemover = i;
                    break;
                }
                String pose = item.getPose() != null ? item.getPose().toLowerCase() : "";
                if (POSES_ENROLLMENT.contains(pose)) {
                    continue;
                }
                double itemScore = item.getScore() != null ? item.getScore() : 0.0;
                if (itemScore < peorScore) {
                    peorScore = itemScore;
                    indiceRemover = i;
                }
            }
            if (indiceRemover < 0) {
                break;
            }
            galeria.getGallery().remove(indiceRemover);
        }
    }

    private List<Double> recalcularMaster(List<EmbeddingGaleriaItemDto> gallery) {
        if (gallery == null || gallery.isEmpty()) {
            return new ArrayList<>();
        }

        List<EmbeddingGaleriaItemDto> validas = new ArrayList<>();
        for (EmbeddingGaleriaItemDto item : gallery) {
            if (item == null || item.getEmbedding() == null || item.getEmbedding().isEmpty()) {
                continue;
            }
            double score = item.getScore() != null ? item.getScore() : 0.0;
            if (score >= SCORE_MINIMO_MASTER) {
                validas.add(item);
            }
        }
        if (validas.isEmpty()) {
            return new ArrayList<>();
        }

        int dim = validas.get(0).getEmbedding().size();
        double[] promedio = new double[dim];
        double pesoTotal = 0.0;

        for (EmbeddingGaleriaItemDto item : validas) {
            double peso = item.getScore() != null ? item.getScore() : 1.0;
            double[] vector = toArray(item.getEmbedding());
            if (vector.length != dim) {
                continue;
            }
            pesoTotal += peso;
            for (int i = 0; i < dim; i++) {
                promedio[i] += vector[i] * peso;
            }
        }
        if (pesoTotal <= 0) {
            return new ArrayList<>();
        }

        for (int i = 0; i < dim; i++) {
            promedio[i] /= pesoTotal;
        }

        double magnitud = 0.0;
        for (double val : promedio) {
            magnitud += val * val;
        }
        magnitud = Math.sqrt(magnitud);
        if (magnitud > 0) {
            for (int i = 0; i < dim; i++) {
                promedio[i] /= magnitud;
            }
        }

        List<Double> master = new ArrayList<>(dim);
        for (double val : promedio) {
            master.add(val);
        }
        return master;
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
