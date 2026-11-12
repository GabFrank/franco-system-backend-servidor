package com.franco.dev.service.personas;

import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaItemDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un enrolamiento "aplastado" no entra (frc-mobile-pwa#144).
 *
 * En WebGL, la GPU de algunos celulares hace que la red devuelva casi el mismo vector para
 * cualquier cara: muchas dimensiones en cero y todas las galerias parecidas entre si. Una
 * galeria asi no distingue a su duenio de otras personas enroladas igual.
 */
class EmbeddingEnrolamientoConfiableTest {

    private final EmbeddingGaleriaService service = new EmbeddingGaleriaService();

    /** Vector como los de Human: no negativo (sale de una ReLU), con una fraccion de ceros. */
    private static List<Double> vector(Random r, double fraccionCeros) {
        List<Double> v = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            v.add(r.nextDouble() < fraccionCeros ? 0.0 : r.nextDouble() * 1.5);
        }
        return v;
    }

    private static EmbeddingGaleriaDto galeria(List<List<Double>> fotos) {
        List<EmbeddingGaleriaItemDto> items = new ArrayList<>();
        for (int i = 0; i < fotos.size(); i++) {
            items.add(new EmbeddingGaleriaItemDto("pose-" + i, fotos.get(i), 1.0));
        }
        return new EmbeddingGaleriaDto(fotos.get(0), items);
    }

    @Test
    void un_enrolamiento_sano_pasa() {
        Random r = new Random(1);
        List<List<Double>> fotos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            fotos.add(vector(r, 0.32)); // mediana de las fotos sanas de produccion
        }
        double[] promedio = service.promedioNormalizado(galeria(fotos));

        assertEquals(1024, promedio.length);
        assertNull(service.motivoEnrolamientoNoConfiable(promedio, 0.6));
    }

    @Test
    void un_enrolamiento_aplastado_no_entra() {
        Random r = new Random(2);
        // Las mismas dimensiones apagadas en todas las fotos: el promedio conserva los ceros.
        List<Double> base = vector(r, 0.56);
        List<List<Double>> fotos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<Double> foto = new ArrayList<>(base);
            for (int k = 0; k < foto.size(); k++) {
                if (foto.get(k) > 0) {
                    foto.set(k, foto.get(k) * (0.9 + 0.2 * r.nextDouble()));
                }
            }
            fotos.add(foto);
        }

        String motivo = service.motivoEnrolamientoNoConfiable(service.promedioNormalizado(galeria(fotos)), 0.3);

        assertNotNull(motivo);
        assertTrue(motivo.contains("no generó un rostro confiable"));
    }

    @Test
    void un_enrolamiento_que_no_se_distingue_de_otra_persona_no_entra() {
        Random r = new Random(3);
        List<List<Double>> fotos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            fotos.add(vector(r, 0.32));
        }
        double[] promedio = service.promedioNormalizado(galeria(fotos));

        assertNull(service.motivoEnrolamientoNoConfiable(promedio, 0.80));
        String motivo = service.motivoEnrolamientoNoConfiable(promedio, 0.95);
        assertNotNull(motivo);
        assertTrue(motivo.contains("se parece demasiado"));
    }

    @Test
    void la_caché_compara_contra_los_masters_de_otras_personas_y_no_contra_el_propio() throws Exception {
        UsuarioEmbeddingCacheService cache = new UsuarioEmbeddingCacheService(null, service);
        java.lang.reflect.Field f = UsuarioEmbeddingCacheService.class.getDeclaredField("entries");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) f.get(cache);
        Class<?> entryClass = Class.forName(UsuarioEmbeddingCacheService.class.getName() + "$CachedEntry");
        java.lang.reflect.Constructor<?> c = entryClass.getDeclaredConstructors()[0];
        c.setAccessible(true);
        List<double[]> propio = new ArrayList<>();
        propio.add(new double[]{1, 0, 0});
        List<double[]> otro = new ArrayList<>();
        otro.add(new double[]{0.6, 0.8, 0});
        entries.add(c.newInstance(1L, propio, null));
        entries.add(c.newInstance(2L, otro, null));

        assertEquals(0.6, cache.maximaSimilitudConOtrosMasters(new double[]{1, 0, 0}, 1L), 1e-9);
        assertEquals(1.0, cache.maximaSimilitudConOtrosMasters(new double[]{1, 0, 0}, 2L), 1e-9);
    }
}
