package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.ConfiguracionMonedaModulo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.ModuloMoneda;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.ConfiguracionMonedaModuloInput;
import com.franco.dev.repository.financiero.CambioRepository;
import com.franco.dev.repository.financiero.ConfiguracionMonedaModuloRepository;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Fija el alta de la moneda por modulo: siempre una fila nueva, sucursal solo en PDV (la decision de
 * negocio, tambien en el CHECK de V258.5), y ninguna moneda sin cotizacion asignada a un modulo.
 */
class ConfiguracionMonedaModuloServiceTest {

    private ConfiguracionMonedaModuloRepository repository;
    private MonedaService monedaService;
    private SucursalService sucursalService;
    private CambioRepository cambioRepository;
    private ConfiguracionMonedaModuloService service;
    private Moneda dolar;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionMonedaModuloRepository.class);
        monedaService = mock(MonedaService.class);
        sucursalService = mock(SucursalService.class);
        cambioRepository = mock(CambioRepository.class);
        service = new ConfiguracionMonedaModuloService(repository, monedaService, sucursalService,
                new CotizacionService(cambioRepository));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        dolar = new Moneda();
        dolar.setId(2L);
        dolar.setDenominacion("DOLAR");
        dolar.setCodigoIso("USD");
        when(monedaService.findById(2L)).thenReturn(Optional.of(dolar));
        Cambio c = new Cambio();
        c.setValorEnGs(5700.0);
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(c);
        Sucursal s = new Sucursal();
        s.setId(7L);
        when(sucursalService.findById(7L)).thenReturn(Optional.of(s));
        autor = new Usuario();
        autor.setId(3L);
    }

    @Test
    void creaUnaFilaNuevaActiva_conElAutorDeLaSesion() {
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.COMPRAS, null, 2L, "2026-11-02 08:00"), autor);
        assertNull(e.getId());
        assertEquals(ModuloMoneda.COMPRAS, e.getModulo());
        assertSame(dolar, e.getMoneda());
        assertNull(e.getSucursal());
        assertTrue(e.getActivo());
        assertSame(autor, e.getUsuario());
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 0), e.getVigenteDesde());
        verify(repository).save(any());
    }

    @Test
    void sinVigencia_rigeDesdeAhora() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.COSTOS, null, 2L, null), autor);
        assertTrue(e.getVigenteDesde().isAfter(antes));
    }

    @Test
    void pdv_admiteSucursal() {
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor);
        assertEquals(7L, e.getSucursal().getId());
    }

    @Test
    void fueraDelPdv_rechazaSucursal() {
        for (ModuloMoneda m : new ModuloMoneda[]{ModuloMoneda.COMPRAS, ModuloMoneda.COSTOS, ModuloMoneda.PRECIOS}) {
            GraphQLException ex = assertThrows(GraphQLException.class,
                    () -> service.crear(input(m, 7L, 2L, null), autor), m.name());
            assertTrue(ex.getMessage().contains("PDV"));
        }
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaUnaMonedaSinCotizacion() {
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(null);
        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
        assertTrue(ex.getMessage().startsWith("SIN_COTIZACION"));
        verify(repository, never()).save(any());
    }

    @Test
    void elGuaraniNoNecesitaCotizacion() {
        Moneda guarani = new Moneda();
        guarani.setId(1L);
        guarani.setDenominacion("GUARANI");
        when(monedaService.findById(1L)).thenReturn(Optional.of(guarani));
        assertSame(guarani, service.crear(input(ModuloMoneda.COMPRAS, null, 1L, null), autor).getMoneda());
    }

    @Test
    void rechazaModuloOMonedaFaltantes_yMonedaInexistente() {
        assertThrows(GraphQLException.class, () -> service.crear(input(null, null, 2L, null), autor));
        assertThrows(GraphQLException.class, () -> service.crear(input(ModuloMoneda.COMPRAS, null, null, null), autor));
        when(monedaService.findById(99L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.crear(input(ModuloMoneda.COMPRAS, null, 99L, null), autor));
    }

    @Test
    void setActivo_soloCambiaElFlag() {
        ConfiguracionMonedaModulo f = new ConfiguracionMonedaModulo();
        f.setId(5L);
        f.setModulo(ModuloMoneda.COMPRAS);
        f.setMoneda(dolar);
        f.setActivo(true);
        when(repository.findById(5L)).thenReturn(Optional.of(f));
        ConfiguracionMonedaModulo e = service.setActivo(5L, false);
        assertFalse(e.getActivo());
        assertSame(dolar, e.getMoneda());
        assertEquals(ModuloMoneda.COMPRAS, e.getModulo());
    }

    private static ConfiguracionMonedaModuloInput input(ModuloMoneda m, Long sucursalId, Long monedaId, String desde) {
        ConfiguracionMonedaModuloInput i = new ConfiguracionMonedaModuloInput();
        i.setModulo(m);
        i.setSucursalId(sucursalId);
        i.setMonedaId(monedaId);
        i.setVigenteDesde(desde);
        return i;
    }
}
