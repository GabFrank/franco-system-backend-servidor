package com.franco.dev.service.personas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaItemDto;
import com.franco.dev.service.personas.dto.IncorporarCapturaMarcacionResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmbeddingGaleriaService {

    private static final double MIN_SCORE_MARCACION = 0.7;
    /**
     * Coseno minimo contra la galeria propia para que una marcacion ensene algo.
     *
     * Calibrado con las galerias de produccion (2026-11-12, 58 personas, 650 capturas
     * de marcacion): con 0,75 un impostor pasa el 0,13 % de las veces y la persona
     * correcta queda afuera el 1,4 %. El 0,55 anterior estaba por debajo de la mediana
     * de dos personas distintas (0,53): dejaba entrar capturas de cualquiera.
     */
    public static final double MIN_SIMILITUD_PROPIA = 0.75;
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
     */
    public IncorporarCapturaMarcacionResult incorporarCapturaMarcacion(
            EmbeddingGaleriaDto galeria,
            List<Double> nuevoEmbedding,
            Double score) {
        if (galeria == null || nuevoEmbedding == null || nuevoEmbedding.isEmpty()) {
            return IncorporarCapturaMarcacionResult.rechazadoSimilitud(
                    "No se encontró galería facial válida para actualizar.");
        }
        double scoreCaptura = score != null ? score : 0.0;
        if (scoreCaptura < MIN_SCORE_MARCACION) {
            return IncorporarCapturaMarcacionResult.rechazadoScore(
                    "La captura no alcanzó la calidad mínima (70%) para actualizar el perfil facial.");
        }

        double[] candidato = toArray(nuevoEmbedding);
        double[] master = toArray(galeria.getMaster());
        if (master.length == 0 || candidato.length != master.length) {
            return IncorporarCapturaMarcacionResult.rechazadoSimilitud(
                    "El perfil facial registrado no es compatible con esta captura.");
        }

        // Contra toda la galeria y no solo el master: el master es un promedio de poses,
        // y una captura buena de frente puede quedar lejos de el y cerca de la foto de frente.
        double similitudPropia = 0.0;
        for (double[] referencia : extraerVectores(galeria)) {
            similitudPropia = Math.max(similitudPropia, cosineSimilarity(candidato, referencia));
        }
        if (similitudPropia < MIN_SIMILITUD_PROPIA) {
            int pct = (int) Math.round(similitudPropia * 100);
            int umbralPct = (int) Math.round(MIN_SIMILITUD_PROPIA * 100);
            return IncorporarCapturaMarcacionResult.rechazadoSimilitud(
                    "Similitud insuficiente (" + pct + "%). Se requiere al menos " + umbralPct + "%.");
        }

        EmbeddingGaleriaDto actualizada = normalizar(galeria);
        if (actualizada.getGallery() == null) {
            actualizada.setGallery(new ArrayList<>());
        }

        int indiceDuplicado = -1;
        for (int i = 0; i < actualizada.getGallery().size(); i++) {
            EmbeddingGaleriaItemDto item = actualizada.getGallery().get(i);
            if (item == null || item.getEmbedding() == null) {
                continue;
            }
            double similitud = cosineSimilarity(candidato, toArray(item.getEmbedding()));
            if (similitud >= MAX_SIMILITUD_DUPLICADO) {
                indiceDuplicado = i;
                break;
            }
        }

        if (indiceDuplicado >= 0) {
            EmbeddingGaleriaItemDto existente = actualizada.getGallery().get(indiceDuplicado);
            double scoreExistente = existente.getScore() != null ? existente.getScore() : 0.0;
            if (scoreCaptura <= scoreExistente) {
                return IncorporarCapturaMarcacionResult.rechazadoScore(
                        "Ya existe una captura similar con igual o mejor calidad en el perfil.");
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
        return IncorporarCapturaMarcacionResult.ok(actualizada);
    }

    /**
     * Saca de a una la captura de marcacion mas redundante (la que mas se parece a otra
     * de la galeria) hasta volver al tope. Las fotos del enrolamiento no se tocan.
     *
     * Antes salia la de menor score, pero todas las capturas de marcacion llegan con
     * score 1,0: en la practica salia la primera de la lista, y la galeria se llenaba de
     * capturas casi iguales. Sacar la redundante deja variedad (luz, lentes, barba), que
     * es lo que hace que la persona se reconozca en condiciones nuevas.
     */
    void podarGaleria(EmbeddingGaleriaDto galeria) {
        if (galeria.getGallery() == null) {
            return;
        }
        galeria.getGallery().removeIf(item -> item == null || item.getEmbedding() == null || item.getEmbedding().isEmpty());
        while (galeria.getGallery().size() > MAX_GALLERY_SIZE) {
            List<EmbeddingGaleriaItemDto> items = galeria.getGallery();
            int indiceRemover = -1;
            double mayorRedundancia = -2.0;
            for (int i = 0; i < items.size(); i++) {
                String pose = items.get(i).getPose() != null ? items.get(i).getPose().toLowerCase() : "";
                if (POSES_ENROLLMENT.contains(pose)) {
                    continue;
                }
                double[] vi = toArray(items.get(i).getEmbedding());
                double redundancia = -1.0;
                for (int j = 0; j < items.size(); j++) {
                    if (j != i) {
                        redundancia = Math.max(redundancia, cosineSimilarity(vi, toArray(items.get(j).getEmbedding())));
                    }
                }
                if (redundancia > mayorRedundancia) {
                    mayorRedundancia = redundancia;
                    indiceRemover = i;
                }
            }
            if (indiceRemover < 0) {
                break;
            }
            items.remove(indiceRemover);
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
            double[] vector = unitario(toArray(item.getEmbedding()));
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

    /**
     * El vector con largo 1. Las fotos del enrolamiento llegan crudas de Human (largo ~11)
     * y las capturas de marcacion ya normalizadas (largo 1): sin esto, al promediar, las
     * tres fotos del enrolamiento pesaban once veces mas y el master no aprendia nada.
     */
    private static double[] unitario(double[] v) {
        double norma = 0.0;
        for (double x : v) {
            norma += x * x;
        }
        norma = Math.sqrt(norma);
        if (norma == 0) {
            return v;
        }
        double[] r = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            r[i] = v[i] / norma;
        }
        return r;
    }

    static double cosineSimilarity(double[] v1, double[] v2) {
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
