package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.domain.financiero.ConfiguracionFacturacionHistorial;
import com.franco.dev.domain.financiero.enums.AccionConfiguracionFacturacion;
import com.franco.dev.domain.financiero.enums.ModoFacturacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionFacturacionInput;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionHistorialRepository;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fija el ABM de la politica de facturacion (issue filial #127): una global y a lo sumo una por
 * sucursal, sin que los indices unicos de V231.1 lleguen a explotar con un error opaco; activo por
 * fila; y una fila de historial por cada cambio.
 */
class ConfiguracionFacturacionServiceTest {

    private ConfiguracionFacturacionRepository repository;
    private ConfiguracionFacturacionHistorialRepository historialRepository;
    private SucursalService sucursalService;
    private ConfiguracionFacturacionService service;
    private Sucursal centro;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionFacturacionRepository.class);
        historialRepository = mock(ConfiguracionFacturacionHistorialRepository.class);
        sucursalService = mock(SucursalService.class);
        service = new ConfiguracionFacturacionService(repository, historialRepository, sucursalService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        centro = new Sucursal();
        centro.setId(7L);
        centro.setNombre("CENTRO");
        when(sucursalService.findById(7L)).thenReturn(Optional.of(centro));
        autor = new Usuario();
        autor.setId(3L);
        autor.setNickname("ADMIN");
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
        c.setModo(ModoFacturacion.INTERVALO);
        c.setVentasSinFactura(4);
        c.setVentaTicketRespetaPolitica(true);
        c.setActivo(true);
        return c;
    }

    private ConfiguracionFacturacionHistorial ultimoHistorial() {
        ArgumentCaptor<ConfiguracionFacturacionHistorial> h = ArgumentCaptor.forClass(ConfiguracionFacturacionHistorial.class);
        verify(historialRepository, atLeastOnce()).save(h.capture());
        List<ConfiguracionFacturacionHistorial> todos = h.getAllValues();
        return todos.get(todos.size() - 1);
    }

    @Test
    void creaLaGlobalCuandoNoHayYDejaHistorial() {
        when(repository.findFirstBySucursalIsNull()).thenReturn(Optional.empty());
        ConfiguracionFacturacion c = service.guardar(input(null, null, ModoFacturacion.TODAS, null), autor);
        assertNull(c.getSucursal());
        assertEquals(ModoFacturacion.TODAS, c.getModo());
        assertEquals(Integer.valueOf(0), c.getVentasSinFactura());
        assertTrue(c.getVentaTicketRespetaPolitica());
        assertTrue(c.getActivo());
        assertNotNull(c.getCreadoEn());
        assertNotNull(c.getModificadoEn());
        // El autor sale de la sesion, no del input, y el schema solo expone su nickname.
        assertSame(autor, c.getUsuario());
        assertEquals("ADMIN", c.getUsuarioNickname());

        ConfiguracionFacturacionHistorial h = ultimoHistorial();
        assertEquals(AccionConfiguracionFacturacion.CREAR, h.getAccion());
        assertEquals(ModoFacturacion.TODAS, h.getModo());
        assertEquals("ADMIN", h.getUsuarioNickname());
    }

    @Test
    void guardarSinIdSobreUnaClaveOcupadaLaActualizaEnVezDeDuplicar() {
        ConfiguracionFacturacion yaEsta = existente(3L, centro);
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(yaEsta));
        ConfiguracionFacturacion c = service.guardar(input(null, 7L, ModoFacturacion.INTERVALO, 2), autor);
        assertSame(yaEsta, c);
        assertEquals(Integer.valueOf(2), c.getVentasSinFactura());
        assertEquals(AccionConfiguracionFacturacion.MODIFICAR, ultimoHistorial().getAccion());
    }

    @Test
    void activoNuloEnUnaEdicionConservaElValor() {
        // Un desktop viejo no manda activo: no puede reactivar una fila desactivada.
        ConfiguracionFacturacion inactiva = existente(3L, centro);
        inactiva.setActivo(false);
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(inactiva));
        ConfiguracionFacturacion c = service.guardar(input(null, 7L, ModoFacturacion.TODAS, 0), autor);
        assertFalse(c.getActivo());
    }

    @Test
    void soloCambiarActivoSeRegistraComoDesactivar() {
        ConfiguracionFacturacion yaEsta = existente(3L, centro);
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(yaEsta));
        ConfiguracionFacturacionInput in = input(null, 7L, ModoFacturacion.INTERVALO, 4);
        in.setActivo(false);
        service.guardar(in, autor);
        ConfiguracionFacturacionHistorial h = ultimoHistorial();
        assertEquals(AccionConfiguracionFacturacion.DESACTIVAR, h.getAccion());
        assertFalse(h.getActivo());
    }

    @Test
    void editarUnaFilaHaciaUnaClaveQueYaTieneOtraFilaSeRechaza() {
        when(repository.findById(5L)).thenReturn(Optional.of(existente(5L, null)));
        when(repository.findFirstBySucursalId(7L)).thenReturn(Optional.of(existente(3L, centro)));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.guardar(input(5L, 7L, ModoFacturacion.TODAS, 0), autor));
        assertTrue(e.getMessage().contains("CENTRO"));
        verify(repository, never()).save(any());
        verify(historialRepository, never()).save(any());
    }

    @Test
    void sinModoONegativoOSucursalInexistenteSeRechazaAntesDeGuardar() {
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, null, null, 0), autor));
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, null, ModoFacturacion.INTERVALO, -1), autor));
        when(sucursalService.findById(99L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.guardar(input(null, 99L, ModoFacturacion.TODAS, 0), autor));
        verify(repository, never()).save(any());
    }

    @Test
    void eliminarGuardaEnElHistorialLosValoresQueTeniaAntesDeBorrar() {
        ConfiguracionFacturacion config = existente(9L, centro);
        when(repository.findById(9L)).thenReturn(Optional.of(config));
        assertTrue(service.eliminar(9L, autor));
        ConfiguracionFacturacionHistorial h = ultimoHistorial();
        assertEquals(AccionConfiguracionFacturacion.ELIMINAR, h.getAccion());
        assertEquals(Long.valueOf(9L), h.getConfiguracionId());
        assertEquals(Integer.valueOf(4), h.getVentasSinFactura());
        assertSame(centro, h.getSucursal());
        // Primero el historial, despues el borrado.
        InOrder orden = inOrder(historialRepository, repository);
        orden.verify(historialRepository).save(any());
        orden.verify(repository).delete(config);
    }

    @Test
    void eliminarUnaQueNoExisteAvisa() {
        when(repository.findById(8L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.eliminar(8L, autor));
        verify(repository, never()).delete(any());
    }

    @Test
    void elMasivoSoloTocaSucursalesQueCambianYDejaUnHistorialPorCada() {
        ConfiguracionFacturacion a = existente(1L, centro);
        ConfiguracionFacturacion b = existente(2L, centro);
        when(repository.findBySucursalIsNotNullAndActivoNotOrderByIdAsc(false)).thenReturn(Arrays.asList(a, b));
        assertEquals(Integer.valueOf(2), service.setActivoSucursales(false, autor));
        assertFalse(a.getActivo());
        assertFalse(b.getActivo());
        verify(historialRepository, times(2)).save(any());
        assertEquals(AccionConfiguracionFacturacion.DESACTIVAR, ultimoHistorial().getAccion());
        // La consulta excluye la global (sucursal NOT NULL) y las que ya estaban en ese estado.
        verify(repository).findBySucursalIsNotNullAndActivoNotOrderByIdAsc(false);
    }

    @Test
    void lasOperacionesQueEscribenSonTransaccionales() throws Exception {
        // Configuracion + historial van juntos o no van: sin esto, cada save se confirma solo.
        Class<?> s = ConfiguracionFacturacionService.class;
        assertNotNull(s.getMethod("guardar", ConfiguracionFacturacionInput.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("eliminar", Long.class, Usuario.class).getAnnotation(Transactional.class));
        assertNotNull(s.getMethod("setActivoSucursales", Boolean.class, Usuario.class).getAnnotation(Transactional.class));
    }

    @Test
    void elHistorialRespetaElLimiteYFiltraLaGlobal() {
        service.historial(null, null);
        verify(historialRepository).findAllByOrderByIdDesc(PageRequest.of(0, 200));
        service.historial(-1L, 5000);
        verify(historialRepository).findBySucursalIsNullOrderByIdDesc(PageRequest.of(0, 1000));
        service.historial(7L, 10);
        verify(historialRepository).findBySucursalIdOrderByIdDesc(7L, PageRequest.of(0, 10));
    }
}
