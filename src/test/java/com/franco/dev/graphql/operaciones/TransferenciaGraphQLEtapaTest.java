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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una transferencia solo avanza. Las transferencias 6284 y 6290 quedaron con el header pisado de
 * vuelta a PRE_TRANSFERENCIA_ORIGEN despues de haberse recepcionado y de que el stock ya se habia
 * movido, porque ni avanzarEtapaTransferencia ni saveTransferencia validaban el orden de etapas.
 */
class TransferenciaGraphQLEtapaTest {

    private TransferenciaService service;
    private TransferenciaGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
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

    private Transferencia persistida(Long id, EtapaTransferencia etapa, TransferenciaEstado estado) {
        Transferencia t = new Transferencia();
        t.setId(id);
        t.setEtapa(etapa);
        t.setEstado(estado);
        when(service.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("avanzarEtapaTransferencia rechaza volver a una etapa anterior y no guarda nada")
    void avanzarNoRetrocede() {
        persistida(6290L, EtapaTransferencia.RECEPCION_CONCLUIDA, TransferenciaEstado.CONLCUIDA);

        assertThrows(GraphQLException.class, () -> resolver.avanzarEtapaTransferencia(
                6290L, EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN, 1L));

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("avanzarEtapaTransferencia sigue permitiendo avanzar")
    void avanzarSiguePermitido() {
        persistida(6291L, EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN, TransferenciaEstado.EN_ORIGEN);

        assertTrue(resolver.avanzarEtapaTransferencia(
                6291L, EtapaTransferencia.PREPARACION_MERCADERIA, 1L));

        verify(service).save(any());
    }

    @Test
    @DisplayName("saveTransferencia rechaza un input que trae una etapa anterior a la persistida")
    void saveNoPisaLaEtapaHaciaAtras() {
        persistida(6284L, EtapaTransferencia.RECEPCION_CONCLUIDA, TransferenciaEstado.CONLCUIDA);

        TransferenciaInput input = new TransferenciaInput();
        input.setId(6284L);
        input.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN);
        input.setEstado(TransferenciaEstado.EN_ORIGEN);

        assertThrows(GraphQLException.class, () -> resolver.saveTransferencia(input));

        verify(service, never()).save(any());
    }
}
