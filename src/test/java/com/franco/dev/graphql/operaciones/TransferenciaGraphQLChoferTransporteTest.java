package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.activos.Vehiculo;
import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.VerificarParaTransporteInput;
import com.franco.dev.service.activos.VehiculoService;
import com.franco.dev.service.operaciones.HojaRutaService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Desde la PWA, la verificacion para transporte arranca eligiendo al chofer: ese usuario queda
 * como responsable de la etapa (no el que esta logueado) y su persona como chofer de una hoja de
 * ruta nueva, con el vehiculo y los acompaniantes del viaje. El desktop sigue usando
 * avanzarEtapaTransferencia y saveHojaRuta sin cambios.
 */
class TransferenciaGraphQLChoferTransporteTest {

    private static final Long TRANSFERENCIA_ID = 500L;
    private static final Long CHOFER_USUARIO_ID = 10L;
    private static final Long CHOFER_PERSONA_ID = 100L;
    private static final Long VEHICULO_ID = 3L;

    private TransferenciaService service;
    private UsuarioService usuarioService;
    private PersonaService personaService;
    private VehiculoService vehiculoService;
    private HojaRutaService hojaRutaService;
    private TransferenciaItemService transferenciaItemService;
    private MovimientoStockService movimientoStockService;
    private TransferenciaGraphQL resolver;

    private Usuario chofer;
    private Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaService.class);
        usuarioService = mock(UsuarioService.class);
        personaService = mock(PersonaService.class);
        vehiculoService = mock(VehiculoService.class);
        hojaRutaService = mock(HojaRutaService.class);
        transferenciaItemService = mock(TransferenciaItemService.class);
        movimientoStockService = mock(MovimientoStockService.class);

        resolver = new TransferenciaGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "personaService", personaService);
        ReflectionTestUtils.setField(resolver, "vehiculoService", vehiculoService);
        ReflectionTestUtils.setField(resolver, "hojaRutaService", hojaRutaService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemService", transferenciaItemService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);
        ReflectionTestUtils.setField(resolver, "transactionManager", mock(PlatformTransactionManager.class));

        chofer = new Usuario();
        chofer.setId(CHOFER_USUARIO_ID);
        chofer.setPersona(persona(CHOFER_PERSONA_ID));
        when(usuarioService.findById(CHOFER_USUARIO_ID)).thenReturn(Optional.of(chofer));

        vehiculo = new Vehiculo();
        vehiculo.setId(VEHICULO_ID);
        when(vehiculoService.findById(VEHICULO_ID)).thenReturn(Optional.of(vehiculo));

        when(personaService.findById(any())).thenAnswer(i -> Optional.of(persona(i.getArgument(0))));
        when(transferenciaItemService.findByTransferenciaId(any())).thenReturn(Collections.emptyList());
        when(transferenciaItemService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(hojaRutaService.save(any())).thenAnswer(i -> {
            HojaRuta h = i.getArgument(0);
            h.setId(900L);
            return h;
        });
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Persona persona(Long id) {
        Persona p = new Persona();
        p.setId(id);
        return p;
    }

    private Transferencia persistida(EtapaTransferencia etapa) {
        Transferencia t = new Transferencia();
        t.setId(TRANSFERENCIA_ID);
        t.setEtapa(etapa);
        t.setEstado(TransferenciaEstado.EN_ORIGEN);
        when(service.findById(TRANSFERENCIA_ID)).thenReturn(Optional.of(t));
        return t;
    }

    private static VerificarParaTransporteInput input(Long... acompanantes) {
        VerificarParaTransporteInput input = new VerificarParaTransporteInput();
        input.setTransferenciaId(TRANSFERENCIA_ID);
        input.setChoferUsuarioId(CHOFER_USUARIO_ID);
        input.setVehiculoId(VEHICULO_ID);
        input.setAcompanantesIds(Arrays.asList(acompanantes));
        return input;
    }

    @Test
    @DisplayName("el chofer elegido queda como responsable y como chofer de una hoja de ruta nueva")
    void choferQuedaResponsableYEnHojaNueva() {
        persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);

        Transferencia resultado = resolver.verificarParaTransporteMobile(input(200L, 201L));

        ArgumentCaptor<HojaRuta> hoja = ArgumentCaptor.forClass(HojaRuta.class);
        verify(hojaRutaService).save(hoja.capture());
        assertEquals(CHOFER_PERSONA_ID, hoja.getValue().getChofer().getId());
        assertSame(vehiculo, hoja.getValue().getVehiculo());
        assertNotNull(hoja.getValue().getFechaSalida());
        assertEquals(Arrays.asList(200L, 201L), ids(hoja.getValue().getAcompanantes()));

        assertEquals(EtapaTransferencia.TRANSPORTE_VERIFICACION, resultado.getEtapa());
        assertSame(chofer, resultado.getUsuarioTransporte());
        assertEquals(900L, resultado.getHojaRuta().getId());
    }

    @Test
    @DisplayName("siempre crea una hoja nueva: la que el desktop haya asignado antes no se toca")
    void reemplazaHojaPreviaSinModificarla() {
        Transferencia t = persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);
        HojaRuta previa = new HojaRuta();
        previa.setId(1L);
        t.setHojaRuta(previa);

        Transferencia resultado = resolver.verificarParaTransporteMobile(input());

        ArgumentCaptor<HojaRuta> hoja = ArgumentCaptor.forClass(HojaRuta.class);
        verify(hojaRutaService).save(hoja.capture());
        assertEquals(900L, resultado.getHojaRuta().getId());
        assertEquals(1L, previa.getId());
        assertEquals(Collections.emptyList(), hoja.getValue().getAcompanantes());
    }

    @Test
    @DisplayName("descarta al chofer y los repetidos de la lista de acompaniantes")
    void filtraAcompanantes() {
        persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);

        resolver.verificarParaTransporteMobile(input(200L, CHOFER_PERSONA_ID, 200L, null));

        ArgumentCaptor<HojaRuta> hoja = ArgumentCaptor.forClass(HojaRuta.class);
        verify(hojaRutaService).save(hoja.capture());
        assertEquals(Collections.singletonList(200L), ids(hoja.getValue().getAcompanantes()));
    }

    @Test
    @DisplayName("copia preparacion a transporte y genera el movimiento de stock de cada item")
    void copiaItemsATransporte() {
        persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);
        TransferenciaItem item = new TransferenciaItem();
        item.setCantidadPreparacion(4.0);
        when(transferenciaItemService.findByTransferenciaId(TRANSFERENCIA_ID))
                .thenReturn(Collections.singletonList(item));

        resolver.verificarParaTransporteMobile(input());

        assertEquals(4.0, item.getCantidadTransporte());
        verify(transferenciaItemService).save(item);
        verify(movimientoStockService).createMovimientoFromTransferenciaItem(item);
    }

    @Test
    @DisplayName("rechaza una transferencia que no esta en PREPARACION_MERCADERIA_CONCLUIDA")
    void rechazaEtapaEquivocada() {
        persistida(EtapaTransferencia.TRANSPORTE_VERIFICACION);

        assertThrows(GraphQLException.class, () -> resolver.verificarParaTransporteMobile(input()));

        verify(hojaRutaService, never()).save(any());
        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("rechaza un chofer sin persona asociada")
    void rechazaChoferSinPersona() {
        persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);
        chofer.setPersona(null);

        assertThrows(GraphQLException.class, () -> resolver.verificarParaTransporteMobile(input()));

        verify(hojaRutaService, never()).save(any());
    }

    @Test
    @DisplayName("rechaza un vehiculo inexistente")
    void rechazaVehiculoInexistente() {
        persistida(EtapaTransferencia.PREPARACION_MERCADERIA_CONCLUIDA);
        when(vehiculoService.findById(VEHICULO_ID)).thenReturn(Optional.empty());

        assertThrows(GraphQLException.class, () -> resolver.verificarParaTransporteMobile(input()));

        verify(hojaRutaService, never()).save(any());
    }

    private static List<Long> ids(List<Persona> personas) {
        return personas.stream().map(Persona::getId).collect(Collectors.toList());
    }
}
