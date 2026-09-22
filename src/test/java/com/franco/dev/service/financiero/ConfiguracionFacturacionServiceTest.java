package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.financiero.enums.ModoFacturacion;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturacionInput;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fija el ABM de la politica de facturacion (issue filial #127): una global y a lo sumo una por
 * sucursal, sin que el indice unico de V230.1 llegue a explotar con un error opaco.
 */
class ConfiguracionFacturacionServiceTest {

    private ConfiguracionFacturacionRepository repository;
    private SucursalService sucursalService;
    private ConfiguracionFacturacionService service;
    private Sucursal centro;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionFacturacionRepository.class);
        sucursalService = mock(SucursalService.class);
        service = new ConfiguracionFacturacionService(repository, sucursalService, mock(UsuarioService.class));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        centro = new Sucursal();
        centro.setId(7L);
        centro.setNombre("CENTRO");
        when(sucursalService.findById(7L)).thenReturn(Optional.of(centro));
    }

    private static ConfiguracionFacturacionInput input(Long id, Long sucursalId, ModoFacturacion modo, Integer n) {
        ConfiguracionFacturacionInput in = new ConfiguracionFacturacionInput();
        in.setId(id);
        in.setSucursalId(sucursalId);
        in.setModo(modo);
        in.setVentasSinFactura(n);
        in.setVentaTicketRespetaPolitica(true);
        return in;
    }

    private static ConfiguracionFacturacion existente(Long id, Sucursal sucursal) {
        ConfiguracionFacturacion c = new ConfiguracionFacturacion();
        c.setId(id);
        c.setSucursal(sucursal);
        return c;
    }

    @Test
    void creaLaGlobalCuandoNoHay() {
        when(repository.findFirstBySucursalIsNull()).thenReturn(Optional.empty());
        ConfiguracionFacturacion c = service.guardar(input(null, null, ModoFacturacion.TODAS, null));
        assertNull(c.getSucursal());
        assertEquals(ModoFacturacion.TODAS, c.getModo());
        assertEquals(Integer.valueOf(0), c.getVentasSinFactura());
        assertTrue(c.getVentaTicketRespetaPolitica());
        assertNotNull(c.getCreadoEn());
        assertNotNull(c.getModificadoEn());
    }

    @Test
    void guardarSinIdSobreUnaClaveOcupadaLaActualizaEnVezDeDuplicar() {
        ConfiguracionFacturacion yaEsta = existente(3L, centro);
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(yaEsta));
        ConfiguracionFacturacion c = service.guardar(input(null, 7L, ModoFacturacion.INTERVALO, 4));
        assertSame(yaEsta, c);
        assertEquals(Integer.valueOf(4), c.getVentasSinFactura());
    }

    @Test
    void editarUnaFilaHaciaUnaClaveQueYaTieneOtraFilaSeRechaza() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, null)));
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(existente(3L, centro)));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardar(input(5L, 7L, ModoFacturacion.TODAS, 0)));
        assertTrue(e.getMessage().contains("CENTRO"));
        verify(repository, never()).save(any());
    }

    @Test
    void sinModoONegativoOSucursalInexistenteSeRechazaAntesDeGuardar() {
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, null, null, 0)));
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, null, ModoFacturacion.INTERVALO, -1)));
        when(sucursalService.findById(99L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, 99L, ModoFacturacion.TODAS, 0)));
        verify(repository, never()).save(any());
    }

    @Test
    void eliminarUnaQueNoExisteAvisa() {
        when(repository.existsById(8L)).thenReturn(false);
        assertThrows(GraphQLException.class, () -> service.eliminar(8L));
        when(repository.existsById(9L)).thenReturn(true);
        assertTrue(service.eliminar(9L));
        verify(repository).deleteById(9L);
    }
}
