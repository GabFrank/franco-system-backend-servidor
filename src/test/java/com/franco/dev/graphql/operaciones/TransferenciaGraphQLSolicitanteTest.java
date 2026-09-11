package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.TransferenciaInput;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El solicitante es el funcionario de la sucursal destino que pidio los productos. Se carga a mano
 * en la etapa de creacion y es obligatorio para salir de ella; no reemplaza a ninguno de los cuatro
 * usuarios del flujo, que siguen siendo quienes operan la transferencia.
 */
class TransferenciaGraphQLSolicitanteTest {

    private static final Long SOLICITANTE_ID = 77L;

    private TransferenciaService service;
    private UsuarioService usuarioService;
    private TransferenciaGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaService.class);
        usuarioService = mock(UsuarioService.class);
        TransferenciaItemService transferenciaItemService = mock(TransferenciaItemService.class);
        MovimientoStockService movimientoStockService = mock(MovimientoStockService.class);

        resolver = new TransferenciaGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemService", transferenciaItemService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);

        when(usuarioService.findById(any())).thenReturn(Optional.of(new Usuario()));
        when(transferenciaItemService.findByTransferenciaId(any())).thenReturn(Collections.emptyList());
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        return u;
    }

    private Transferencia persistida(Long id, EtapaTransferencia etapa, Usuario solicitante) {
        Transferencia t = new Transferencia();
        t.setId(id);
        t.setEtapa(etapa);
        t.setEstado(TransferenciaEstado.ABIERTA);
        t.setSolicitante(solicitante);
        when(service.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("no se puede salir de la etapa de creacion sin solicitante")
    void avanzarSinSolicitanteFalla() {
        persistida(7001L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);

        assertThrows(GraphQLException.class, () -> resolver.avanzarEtapaTransferencia(
                7001L, EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN, 1L));

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("saltear etapas hacia adelante tampoco esquiva el solicitante obligatorio")
    void saltarEtapasSinSolicitanteFalla() {
        persistida(7002L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);

        assertThrows(GraphQLException.class, () -> resolver.avanzarEtapaTransferencia(
                7002L, EtapaTransferencia.PREPARACION_MERCADERIA, 1L));

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("con solicitante cargado la transferencia avanza normalmente")
    void avanzarConSolicitanteFunciona() {
        persistida(7003L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, usuario(SOLICITANTE_ID));

        assertTrue(resolver.avanzarEtapaTransferencia(
                7003L, EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN, 1L));

        verify(service).save(any());
    }

    @Test
    @DisplayName("una transferencia anterior a la columna, ya pasada la creacion, no queda trabada")
    void transferenciaViejaSinSolicitanteSigueAvanzando() {
        persistida(7004L, EtapaTransferencia.TRANSPORTE_EN_CAMINO, null);

        assertTrue(resolver.avanzarEtapaTransferencia(
                7004L, EtapaTransferencia.RECEPCION_EN_VERIFICACION, 1L));

        verify(service).save(any());
    }

    @Test
    @DisplayName("el save tampoco deja avanzar la etapa sin solicitante")
    void saveNoAvanzaEtapaSinSolicitante() {
        persistida(7005L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);

        TransferenciaInput input = new TransferenciaInput();
        input.setId(7005L);
        input.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN);

        assertThrows(GraphQLException.class, () -> resolver.saveTransferencia(input));

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("el save que no mueve la etapa sigue andando sin solicitante")
    void saveEnLaMismaEtapaSinSolicitanteFunciona() {
        persistida(7006L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);

        TransferenciaInput input = new TransferenciaInput();
        input.setId(7006L);
        input.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);

        resolver.saveTransferencia(input);

        verify(service).save(any());
    }

    @Test
    @DisplayName("finalizarTransferencia tampoco cierra la creacion sin solicitante")
    void finalizarSinSolicitanteFalla() {
        persistida(7009L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);

        assertThrows(GraphQLException.class, () -> resolver.finalizarTransferencia(7009L, 1L));

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("finalizarTransferencia con solicitante cierra la creacion normalmente")
    void finalizarConSolicitanteFunciona() {
        persistida(7010L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, usuario(SOLICITANTE_ID));

        assertTrue(resolver.finalizarTransferencia(7010L, 1L));

        verify(service).save(any());
    }

    @Test
    @DisplayName("un save que no manda solicitanteId no borra el solicitante ya guardado")
    void savePreservaElSolicitante() {
        persistida(7007L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, usuario(SOLICITANTE_ID));

        TransferenciaInput input = new TransferenciaInput();
        input.setId(7007L);
        input.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);

        resolver.saveTransferencia(input);

        ArgumentCaptor<Transferencia> captor = ArgumentCaptor.forClass(Transferencia.class);
        verify(service).save(captor.capture());
        assertEquals(SOLICITANTE_ID, captor.getValue().getSolicitante().getId());
    }

    @Test
    @DisplayName("el solicitante que llega en el input se resuelve y se guarda")
    void saveAsignaElSolicitanteDelInput() {
        persistida(7008L, EtapaTransferencia.PRE_TRANSFERENCIA_CREACION, null);
        when(usuarioService.findById(SOLICITANTE_ID)).thenReturn(Optional.of(usuario(SOLICITANTE_ID)));

        TransferenciaInput input = new TransferenciaInput();
        input.setId(7008L);
        input.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);
        input.setSolicitanteId(SOLICITANTE_ID);

        resolver.saveTransferencia(input);

        ArgumentCaptor<Transferencia> captor = ArgumentCaptor.forClass(Transferencia.class);
        verify(service).save(captor.capture());
        assertEquals(SOLICITANTE_ID, captor.getValue().getSolicitante().getId());
    }
}
