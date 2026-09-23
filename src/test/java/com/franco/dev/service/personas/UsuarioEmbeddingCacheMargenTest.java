package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.UsuarioSimilitudResult;
import com.franco.dev.repository.personas.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El 1:N devuelve el mejor match; sin el segundo candidato ese numero no dice nada.
 *
 * Una similitud de 0,71 contra un segundo de 0,45 es una identificacion solida. La misma
 * 0,71 contra un segundo de 0,69 es una moneda al aire, y hasta ahora las dos llegaban
 * al cliente exactamente iguales: findBestMatch calculaba el maximo y descartaba el resto.
 *
 * Sin el margen, un falso positivo es indistinguible de un acierto en los datos. Con el
 * margen se puede fijar un umbral con evidencia de esta poblacion en vez de copiarlo.
 */
class UsuarioEmbeddingCacheMargenTest {

    private UsuarioRepository usuarioRepository;
    private UsuarioEmbeddingCacheService cache;

    /** Base ortonormal: el coseno contra cada eje es directamente su componente. */
    private static final List<Double> CONSULTA = Arrays.asList(1.0, 0.0, 0.0);

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        cache = new UsuarioEmbeddingCacheService(usuarioRepository, new EmbeddingGaleriaService());
    }

    /**
     * Un usuario cuya galeria es un solo vector, escrito como lo guarda la app.
     *
     * `parecido` es el coseno que va a dar contra {@link #CONSULTA}: se arma el vector
     * (p, sqrt(1-p^2), 0), que tiene norma 1 y proyeccion p sobre el primer eje.
     */
    private Usuario usuarioConParecido(long id, double parecido) {
        double resto = Math.sqrt(Math.max(0d, 1d - parecido * parecido));
        String json = "{\"master\":[" + parecido + "," + resto + ",0.0],"
                + "\"gallery\":[{\"pose\":\"front\",\"embedding\":["
                + parecido + "," + resto + ",0.0],\"score\":0.9}]}";

        Persona persona = new Persona();
        persona.setId(id * 10);
        persona.setNombre("Persona " + id);
        persona.setEmbedding(json);

        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNickname("USUARIO" + id);
        usuario.setActivo(true);
        usuario.setPersona(persona);
        return usuario;
    }

    private void enCache(Usuario... usuarios) {
        when(usuarioRepository.findActivosConEmbedding()).thenReturn(new ArrayList<>(Arrays.asList(usuarios)));
        cache.refreshAll();
    }

    @Test
    void sinNadieEnLaCacheNoHayMatch() {
        enCache();

        assertNull(cache.findBestMatch(CONSULTA, Collections.emptyList()));
    }

    @Test
    void devuelveElMejorDeTodos() {
        enCache(usuarioConParecido(1, 0.45), usuarioConParecido(2, 0.71), usuarioConParecido(3, 0.30));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.emptyList());

        assertNotNull(r);
        assertEquals(2L, r.getUsuario().getId());
        assertEquals(0.71, r.getSimilitud(), 0.001);
    }

    @Test
    void informaLaSimilitudDelSegundoCandidato() {
        enCache(usuarioConParecido(1, 0.45), usuarioConParecido(2, 0.71));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.emptyList());

        assertEquals(0.45, r.getSimilitudSegundo(), 0.001);
    }

    @Test
    void elMargenEsLaDistanciaAlSegundo() {
        enCache(usuarioConParecido(1, 0.45), usuarioConParecido(2, 0.71));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.emptyList());

        assertEquals(0.26, r.getMargen(), 0.001);
    }

    @Test
    void unMargenChicoDelataLaMonedaAlAire() {
        // Mismo 0,71 que el caso solido, y el numero absoluto no lo distingue.
        enCache(usuarioConParecido(1, 0.69), usuarioConParecido(2, 0.71));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.emptyList());

        assertEquals(0.71, r.getSimilitud(), 0.001);
        assertTrue(r.getMargen() < 0.05, "el margen tiene que delatar que estuvo ajustado");
    }

    @Test
    void conUnSoloEnroladoNoHaySegundoYElMargenNoSeInventa() {
        enCache(usuarioConParecido(2, 0.71));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.emptyList());

        // Ni 0 ni 1: no hay contra quien comparar, y decir "margen 1" afirmaria una
        // certeza que nadie midio.
        assertNull(r.getSimilitudSegundo());
        assertNull(r.getMargen());
    }

    @Test
    void elExcluidoNoCuentaComoSegundo() {
        enCache(usuarioConParecido(1, 0.69), usuarioConParecido(2, 0.71), usuarioConParecido(3, 0.30));

        UsuarioSimilitudResult r = cache.findBestMatch(CONSULTA, Collections.singletonList(1));

        assertEquals(2L, r.getUsuario().getId());
        assertEquals(0.30, r.getSimilitudSegundo(), 0.001);
    }
}
