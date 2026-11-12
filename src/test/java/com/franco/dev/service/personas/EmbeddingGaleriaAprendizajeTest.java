package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.enums.ResultadoIncorporacionEmbedding;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaItemDto;
import com.franco.dev.service.personas.dto.IncorporarCapturaMarcacionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aprendizaje de la galeria facial con las capturas de cada marcacion.
 *
 * Las fotos del enrolamiento llegan crudas de Human (largo ~11) y las capturas de
 * marcacion normalizadas (largo 1). Se mide todo con coseno, que no depende del largo.
 */
class EmbeddingGaleriaAprendizajeTest {

    private final EmbeddingGaleriaService service = new EmbeddingGaleriaService();

    private static List<Double> v(double... xs) {
        List<Double> l = new ArrayList<>();
        for (double x : xs) {
            l.add(x);
        }
        return l;
    }

    private static List<Double> escalar(List<Double> v, double k) {
        List<Double> r = new ArrayList<>();
        for (Double x : v) {
            r.add(x * k);
        }
        return r;
    }

    private static double coseno(List<Double> a, List<Double> b) {
        double[] x = a.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = b.stream().mapToDouble(Double::doubleValue).toArray();
        return EmbeddingGaleriaService.cosineSimilarity(x, y);
    }

    /** Galeria con las tres poses del enrolamiento, crudas (largo ~11). */
    private EmbeddingGaleriaDto galeriaEnrolada() {
        List<EmbeddingGaleriaItemDto> items = new ArrayList<>(Arrays.asList(
                new EmbeddingGaleriaItemDto("left", escalar(v(1, 0.2, 0, 0), 11), 1.0),
                new EmbeddingGaleriaItemDto("right", escalar(v(1, 0, 0.2, 0), 11), 1.0),
                new EmbeddingGaleriaItemDto("front", escalar(v(1, 0.1, 0.1, 0), 11), 1.0)));
        return new EmbeddingGaleriaDto(v(1, 0.1, 0.1, 0), items);
    }

    @Test
    void lo_aprendido_mueve_el_master_aunque_el_enrolamiento_venga_crudo() {
        EmbeddingGaleriaDto galeria = galeriaEnrolada();
        // Captura normalizada (largo 1), parecida pero con algo de la componente 4.
        List<Double> captura = v(0.9, 0.1, 0.1, 0.4);
        double antes = coseno(galeria.getMaster(), captura);

        IncorporarCapturaMarcacionResult r = service.incorporarCapturaMarcacion(galeria, escalar(captura, 1 / Math.sqrt(0.99)), 1.0);

        assertEquals(ResultadoIncorporacionEmbedding.OK, r.getResultado());
        double despues = coseno(r.getGaleria().getMaster(), captura);
        // Con el promedio sin normalizar la captura pesaba 1/11 de cada pose: el master casi no se movia.
        assertTrue(despues - antes > 0.02, "el master tiene que acercarse a lo aprendido: " + antes + " -> " + despues);
    }

    @Test
    void rechaza_una_captura_por_debajo_del_umbral_propio() {
        EmbeddingGaleriaDto galeria = galeriaEnrolada();
        // Coseno ~0,7 contra la galeria: es la zona de dos personas distintas.
        IncorporarCapturaMarcacionResult r = service.incorporarCapturaMarcacion(galeria, v(0.7, 0, 0, 0.714), 1.0);
        assertEquals(ResultadoIncorporacionEmbedding.RECHAZADO_SIMILITUD, r.getResultado());
    }

    @Test
    void la_poda_saca_la_captura_mas_redundante_y_nunca_el_enrolamiento() {
        EmbeddingGaleriaDto galeria = galeriaEnrolada();
        List<EmbeddingGaleriaItemDto> items = galeria.getGallery();
        // 12 capturas casi iguales entre si y una distinta (otra luz, por ejemplo).
        for (int i = 0; i < 12; i++) {
            items.add(new EmbeddingGaleriaItemDto("marcacion-" + i, v(1, 0.1, 0.1, 0.01 * i), 1.0));
        }
        items.add(new EmbeddingGaleriaItemDto("marcacion-distinta", v(1, 0.1, 0.1, 0.6), 1.0));
        items.add(new EmbeddingGaleriaItemDto("marcacion-extra", v(1, 0.1, 0.1, 0.005), 1.0));
        assertEquals(17, items.size());

        service.podarGaleria(galeria);

        assertEquals(15, items.size());
        assertTrue(items.stream().anyMatch(i -> "marcacion-distinta".equals(i.getPose())), "la captura que aporta variedad se queda");
        for (String pose : Arrays.asList("left", "right", "front")) {
            assertTrue(items.stream().anyMatch(i -> pose.equals(i.getPose())), "el enrolamiento no se poda: " + pose);
        }
    }
}
