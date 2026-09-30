package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.graphql.rrhh.LiquidacionConceptoGraphQL;
import com.franco.dev.graphql.rrhh.input.LiquidacionConceptoInput;
import com.franco.dev.repository.rrhh.LiquidacionConceptoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Número fijo de operación: único entre activos, 0 lo quita, un cliente viejo no lo pisa. */
class LiquidacionConceptoNumeroTest {

    private LiquidacionConceptoRepository repository;
    private LiquidacionConceptoService service;
    private LiquidacionConcepto bonoManual;

    @BeforeEach
    void setUp() {
        repository = mock(LiquidacionConceptoRepository.class);
        service = new LiquidacionConceptoService(repository);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.findByNumeroAndActivoTrue(anyInt())).thenReturn(List.of());

        bonoManual = concepto(3L, "BONO_MANUAL", "BONO MANUAL", 3, true);
        when(repository.findByNumeroAndActivoTrue(3)).thenReturn(List.of(bonoManual));
    }

    private static LiquidacionConcepto concepto(Long id, String codigo, String desc, Integer numero, boolean activo) {
        LiquidacionConcepto c = new LiquidacionConcepto();
        c.setId(id);
        c.setCodigo(codigo);
        c.setDescripcion(desc);
        c.setNumero(numero);
        c.setActivo(activo);
        c.setEsHaber(true);
        c.setEsCalculadoAuto(false);
        c.setEsRemunerativo(true);
        return c;
    }

    // ─────────────────────────── unicidad ───────────────────────────

    @Test
    void unNumeroQueYaUsaOtraOperacionActivaSeRechaza() {
        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", 3, true)));
        assertTrue(ex.getMessage().contains("BONO MANUAL"), ex.getMessage());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void laMismaOperacionConservaSuNumero() {
        assertDoesNotThrow(() -> service.save(bonoManual));
    }

    @Test
    void unaOperacionInactivaNoOcupaNiReclamaNumero() {
        // Inactiva con el 3: no choca (el indice es solo entre activos)...
        assertDoesNotThrow(() -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", 3, false)));
    }

    @Test
    void reactivarConUnNumeroQueTomoOtraSeRechaza() {
        LiquidacionConcepto viejo = concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", 3, false);
        viejo.setActivo(true);

        assertThrows(GraphQLException.class, () -> service.save(viejo));
    }

    @Test
    void ceroONegativoNoEsUnNumeroValido() {
        assertThrows(GraphQLException.class,
                () -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", -1, true)));
    }

    @Test
    void dosOperacionesSinNumeroNoChocan() {
        assertDoesNotThrow(() -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", null, true)));
        assertDoesNotThrow(() -> service.save(concepto(21L, "PREMIO", "PREMIO", null, true)));
    }

    @Test
    void laViolacionDelIndicePorEdicionSimultaneaSaleComoMensaje() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup",
                new RuntimeException("duplicate key value violates unique constraint \"uq_liquidacion_concepto_numero_activo\"")));

        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", 5, true)));
        assertTrue(ex.getMessage().contains("5"), ex.getMessage());
    }

    @Test
    void otraViolacionDeIntegridadNoSeDisfrazaDeNumeroRepetido() {
        DataIntegrityViolationException otra = new DataIntegrityViolationException("dup",
                new RuntimeException("duplicate key value violates unique constraint \"liquidacion_concepto_codigo_key\""));
        when(repository.saveAndFlush(any())).thenThrow(otra);

        assertThrows(DataIntegrityViolationException.class,
                () -> service.save(concepto(20L, "AJUSTE_HABER", "AJUSTE (HABER)", 5, true)));
    }

    // ─────────────────────────── orden ───────────────────────────

    @Test
    void elSelectSeOrdenaPorNumeroYDespuesComoSiempre() {
        service.findParaItemManual();

        verify(repository).findByActivoTrueAndEsCalculadoAutoFalse(argThat((Sort s) ->
                s.getOrderFor("numero") != null && s.getOrderFor("numero").isAscending()
                        && s.getOrderFor("esHaber") != null && s.getOrderFor("esHaber").isDescending()
                        && s.getOrderFor("codigo") != null));
    }

    // ─────────────────────────── resolver ───────────────────────────

    private LiquidacionConceptoGraphQL resolver() {
        LiquidacionConceptoGraphQL r = new LiquidacionConceptoGraphQL();
        ReflectionTestUtils.setField(r, "service", service);
        ReflectionTestUtils.setField(r, "seg", mock(RrhhSecurityService.class));
        ReflectionTestUtils.setField(r, "usuarioService", mock(UsuarioService.class));
        ReflectionTestUtils.setField(r, "liquidacionItemRepository", mock(LiquidacionItemRepository.class));
        when(repository.findById(3L)).thenReturn(Optional.of(bonoManual));
        when(repository.findByCodigo(anyString())).thenReturn(Optional.of(bonoManual));
        return r;
    }

    @Test
    void unDesktopViejoQueNoMandaElNumeroLoConserva() {
        LiquidacionConceptoInput in = new LiquidacionConceptoInput();
        in.setId(3L);
        in.setCodigo("BONO_MANUAL");
        in.setDescripcion("BONO MANUAL");

        LiquidacionConcepto res = resolver().saveLiquidacionConcepto(in);

        assertEquals(3, res.getNumero());
    }

    @Test
    void ceroQuitaElNumero() {
        LiquidacionConceptoInput in = new LiquidacionConceptoInput();
        in.setId(3L);
        in.setCodigo("BONO_MANUAL");
        in.setDescripcion("BONO MANUAL");
        in.setNumero(0);

        LiquidacionConcepto res = resolver().saveLiquidacionConcepto(in);

        assertNull(res.getNumero());
    }
}
