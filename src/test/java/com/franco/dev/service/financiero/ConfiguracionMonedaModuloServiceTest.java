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
import static org.mockito.ArgumentMatchers.anyString;
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
    private ControlVersionMoneda controlVersion;
    private ConfiguracionMonedaModuloService service;
    private Moneda dolar;
    private Usuario autor;

    @BeforeEach
    void setUp() {
        repository = mock(ConfiguracionMonedaModuloRepository.class);
        monedaService = mock(MonedaService.class);
        sucursalService = mock(SucursalService.class);
        cambioRepository = mock(CambioRepository.class);
        controlVersion = mock(ControlVersionMoneda.class);
        service = new ConfiguracionMonedaModuloService(repository, monedaService, sucursalService,
                new CotizacionService(cambioRepository), controlVersion);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        dolar = new Moneda();
        dolar.setId(2L);
        dolar.setDenominacion("DOLAR");
        dolar.setCodigoIso("USD");
        when(monedaService.findById(2L)).thenReturn(Optional.of(dolar));
        Cambio c = new Cambio();
        c.setValorEnGs(5700.0);
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(c);
        Cambio ultimo = new Cambio();
        ultimo.setValorEnGs(5700.0);
        ultimo.setValorEnGsCompraMercado(5900.0);
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(ultimo);
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
    void costosEnOtraMoneda_guardaLaCotizacionDeCompraComoPartida() {
        // La de compras (mercado primero), no la del comercio: es la que usa el costo.
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.COSTOS, null, 2L, null), autor);
        assertEquals(0, new java.math.BigDecimal("5900").compareTo(e.getCotizacionPartida()));
    }

    @Test
    void costosSinCotizacionDeCompras_seRechaza() {
        when(cambioRepository.findLastByCambioId(2L)).thenReturn(null);
        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.crear(input(ModuloMoneda.COSTOS, null, 2L, null), autor));
        assertTrue(ex.getMessage().startsWith("SIN_COTIZACION"));
        verify(repository, never()).save(any());
    }

    @Test
    void costosEnLaMonedaDeLaEmpresa_yLosDemasModulos_noGuardanPartida() {
        assertNull(service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor).getCotizacionPartida());
        when(monedaService.empresa()).thenReturn(dolar);
        assertNull(service.crear(input(ModuloMoneda.COSTOS, null, 2L, null), autor).getCotizacionPartida());
    }

    @Test
    void pdv_admiteSucursal() {
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor);
        assertEquals(7L, e.getSucursal().getId());
    }

    @Test
    void facturacion_admiteSucursal() {
        ConfiguracionMonedaModulo e = service.crear(input(ModuloMoneda.FACTURACION, 7L, 2L, null), autor);
        assertEquals(7L, e.getSucursal().getId());
        assertEquals(ModuloMoneda.FACTURACION, e.getModulo());
    }

    @Test
    void pdvYFacturacionEnOtraMoneda_exigenLaVersionMinima() {
        service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor);
        service.crear(input(ModuloMoneda.FACTURACION, 7L, 2L, null), autor);
        verify(controlVersion, times(2)).exigirParaOtraMoneda(anyString());
    }

    @Test
    void sinVersionMinima_elPdvEnOtraMonedaNoSeGuarda() {
        doThrow(new GraphQLException("VERSION_MINIMA: x")).when(controlVersion).exigirParaOtraMoneda(anyString());
        assertThrows(GraphQLException.class, () -> service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor));
        verify(repository, never()).save(any());
    }

    @Test
    void pdvEnLaMonedaDeLaEmpresa_yLosDemasModulos_noExigenVersion() {
        when(monedaService.empresa()).thenReturn(dolar);
        service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor);
        when(monedaService.empresa()).thenReturn(null);
        service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor);
        verify(controlVersion, never()).exigirParaOtraMoneda(anyString());
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

    // Cotizacion vieja: el comercio la cargo hace meses. No bloquea, pero hay que confirmarla.

    private void cotizacionDelComercioCargadaHace(int dias) {
        Cambio c = new Cambio();
        c.setValorEnGs(5700.0);
        c.setCreadoEn(LocalDateTime.now().minusDays(dias));
        when(cambioRepository.findVigenteDelComercio(eq(2L), any())).thenReturn(c);
    }

    @Test
    void cotizacionDeMasDe30Dias_sinConfirmar_seRechazaConLaFecha() {
        cotizacionDelComercioCargadaHace(45);
        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
        assertTrue(ex.getMessage().startsWith("COTIZACION_VIEJA"), ex.getMessage());
        assertTrue(ex.getMessage().contains("DOLAR"), ex.getMessage());
        String fecha = LocalDateTime.now().minusDays(45).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertTrue(ex.getMessage().contains(fecha), ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void cotizacionDeMasDe30Dias_confirmada_seGuarda() {
        cotizacionDelComercioCargadaHace(45);
        ConfiguracionMonedaModuloInput i = input(ModuloMoneda.COMPRAS, null, 2L, null);
        i.setConfirmarCotizacionVieja(true);
        assertNotNull(service.crear(i, autor));
        verify(repository).save(any());
    }

    @Test
    void cotizacionDeHastaTreintaDias_noPideConfirmar() {
        cotizacionDelComercioCargadaHace(29);
        assertNotNull(service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
    }

    @Test
    void cotizacionVieja_enLaMonedaDeLaEmpresa_noPideConfirmar() {
        // La moneda de la empresa no se convierte: su cotizacion no se usa.
        cotizacionDelComercioCargadaHace(400);
        when(monedaService.empresa()).thenReturn(dolar);
        assertNotNull(service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
    }

    // Sin repetidos: una fila con la moneda que ya rige en su clave no se crea.

    private ConfiguracionMonedaModulo fila(long id, ModuloMoneda m, Long sucursalId, Moneda moneda,
                                           LocalDateTime desde, boolean activo) {
        ConfiguracionMonedaModulo f = new ConfiguracionMonedaModulo();
        f.setId(id);
        f.setModulo(m);
        if (sucursalId != null) {
            Sucursal s = new Sucursal();
            s.setId(sucursalId);
            f.setSucursal(s);
        }
        f.setMoneda(moneda);
        f.setVigenteDesde(desde);
        f.setActivo(activo);
        return f;
    }

    private void filasDe(ModuloMoneda m, ConfiguracionMonedaModulo... filas) {
        when(repository.findByModuloOrderByVigenteDesdeDescIdDesc(m)).thenReturn(java.util.Arrays.asList(filas));
    }

    private Moneda guarani() {
        Moneda g = new Moneda();
        g.setId(1L);
        g.setDenominacion("GUARANI");
        when(monedaService.findById(1L)).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    void igualALaQueRige_seRechazaConLaFecha() {
        filasDe(ModuloMoneda.PDV, fila(10, ModuloMoneda.PDV, 7L, dolar, LocalDateTime.of(2026, 11, 10, 15, 0), true));
        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor));
        assertTrue(ex.getMessage().contains("PDV de la sucursal"), ex.getMessage());
        assertTrue(ex.getMessage().contains("ya usa DOLAR desde el 10/11/2026"), ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void costos_igualALaQueRige_seGuarda_paraReFijarLaPartida() {
        filasDe(ModuloMoneda.COSTOS, fila(10, ModuloMoneda.COSTOS, null, dolar, LocalDateTime.of(2026, 11, 10, 15, 0), true));
        assertNotNull(service.crear(input(ModuloMoneda.COSTOS, null, 2L, null), autor));
        verify(repository).save(any());
    }

    @Test
    void otraMonedaQueLaQueRige_seGuarda() {
        filasDe(ModuloMoneda.COMPRAS, fila(10, ModuloMoneda.COMPRAS, null, guarani(), LocalDateTime.of(2026, 11, 10, 15, 0), true));
        assertNotNull(service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
    }

    @Test
    void igualAUnaDesactivada_seGuarda() {
        filasDe(ModuloMoneda.COMPRAS, fila(10, ModuloMoneda.COMPRAS, null, dolar, LocalDateTime.of(2026, 11, 10, 15, 0), false));
        assertNotNull(service.crear(input(ModuloMoneda.COMPRAS, null, 2L, null), autor));
    }

    @Test
    void sucursalIgualALaDeTodaLaEmpresa_seGuarda() {
        // La clave es exacta: la fila de la sucursal la fija ante un cambio futuro de la global.
        filasDe(ModuloMoneda.PDV, fila(10, ModuloMoneda.PDV, null, dolar, LocalDateTime.of(2026, 11, 10, 15, 0), true));
        assertNotNull(service.crear(input(ModuloMoneda.PDV, 7L, 2L, null), autor));
    }

    @Test
    void conUnaProgramadaDistintaEnElMedio_sePuedeVolverDespues() {
        Moneda g = guarani();
        filasDe(ModuloMoneda.PRECIOS,
                fila(11, ModuloMoneda.PRECIOS, null, g, LocalDateTime.of(2030, 1, 1, 0, 0), true),
                fila(10, ModuloMoneda.PRECIOS, null, dolar, LocalDateTime.of(2026, 11, 10, 15, 0), true));
        // Despues de la programada en guaranies, volver a dolar es un cambio real.
        assertNotNull(service.crear(input(ModuloMoneda.PRECIOS, null, 2L, "2030-02-01 00:00"), autor));
        // Antes de ella rige el dolar: repetirlo se rechaza aunque la fecha sea futura.
        assertThrows(GraphQLException.class,
                () -> service.crear(input(ModuloMoneda.PRECIOS, null, 2L, "2029-06-01 00:00"), autor));
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
