package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionInput;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * saveNotaRecepcion descartaba esNotaRechazo (el input Java no tenia el campo) y dejaba creado_en
 * nulo cuando el cliente no lo mandaba, como el dialogo «Rechazar item» del desktop (#439).
 * En edicion, lo que no viene en el input se conserva de la nota guardada.
 */
class NotaRecepcionGraphQLSaveTest {

    private static final Long NOTA_ID = 12L;
    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 10, 15, 8, 47, 0);

    private NotaRecepcionService service;
    private NotaRecepcionGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(NotaRecepcionService.class);
        resolver = new NotaRecepcionGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(service.findById(any())).thenReturn(Optional.empty());
    }

    private NotaRecepcion guardar(NotaRecepcionInput input) {
        resolver.saveNotaRecepcion(input);
        ArgumentCaptor<NotaRecepcion> c = ArgumentCaptor.forClass(NotaRecepcion.class);
        verify(service).save(c.capture());
        return c.getValue();
    }

    private void notaGuardada(boolean esNotaRechazo) {
        NotaRecepcion n = new NotaRecepcion();
        n.setId(NOTA_ID);
        n.setEsNotaRechazo(esNotaRechazo);
        n.setCreadoEn(CREADO);
        when(service.findById(NOTA_ID)).thenReturn(Optional.of(n));
    }

    private NotaRecepcionInput edicion() {
        NotaRecepcionInput in = new NotaRecepcionInput();
        in.setId(NOTA_ID);
        in.setNumero(488504);
        return in;
    }

    @Test
    void altaDeNotaDeRechazoGuardaElFlag() {
        NotaRecepcionInput in = new NotaRecepcionInput();
        in.setEsNotaRechazo(true);
        assertTrue(guardar(in).getEsNotaRechazo());
    }

    @Test
    void altaSinFlagQuedaEnFalseYNoEnNull() {
        assertEquals(Boolean.FALSE, guardar(new NotaRecepcionInput()).getEsNotaRechazo());
    }

    @Test
    void altaSinCreadoEnLoCompletaElServidor() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        LocalDateTime creadoEn = guardar(new NotaRecepcionInput()).getCreadoEn();
        assertNotNull(creadoEn);
        assertTrue(creadoEn.isAfter(antes));
    }

    @Test
    void altaConCreadoEnRespetaElDelCliente() {
        NotaRecepcionInput in = new NotaRecepcionInput();
        in.setCreadoEn("2026-10-15 08:47:00");
        assertEquals(CREADO, guardar(in).getCreadoEn());
    }

    @Test
    void edicionSinLosCamposConservaLosGuardados() {
        notaGuardada(true);
        NotaRecepcion e = guardar(edicion());
        assertTrue(e.getEsNotaRechazo());
        assertEquals(CREADO, e.getCreadoEn());
    }

    @Test
    void edicionConFlagExplicitoLoRespeta() {
        notaGuardada(true);
        NotaRecepcionInput in = edicion();
        in.setEsNotaRechazo(false);
        NotaRecepcion e = guardar(in);
        assertFalse(e.getEsNotaRechazo());
        assertEquals(CREADO, e.getCreadoEn());
    }

    @Test
    void edicionConCreadoEnYSinFlagConservaSoloElFlag() {
        notaGuardada(true);
        NotaRecepcionInput in = edicion();
        in.setCreadoEn("2026-10-16 09:00:00");
        NotaRecepcion e = guardar(in);
        assertTrue(e.getEsNotaRechazo());
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 0, 0), e.getCreadoEn());
    }

    @Test
    void edicionDeIdInexistenteUsaLosValoresDeAlta() {
        NotaRecepcion e = guardar(edicion());
        assertEquals(Boolean.FALSE, e.getEsNotaRechazo());
        assertNotNull(e.getCreadoEn());
    }
}
