package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.TipoOperacionFinanciera;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.OperacionFinancieraGraphQL.OperacionFinancieraInputWrapper;
import com.franco.dev.graphql.financiero.PagoProveedorGraphQL.GastoParaPagoWrapper;
import com.franco.dev.graphql.financiero.input.EntradaVariaInput;
import com.franco.dev.graphql.rrhh.PrestamoGraphQL;
import com.franco.dev.graphql.rrhh.ValeTesoreriaGraphQL;
import com.franco.dev.graphql.rrhh.ValeTesoreriaGraphQL.ValeParaPagoWrapper;
import com.franco.dev.graphql.rrhh.input.PrestamoInput;
import com.franco.dev.service.financiero.AltaIdempotenteService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.EntradaVariaService;
import com.franco.dev.service.financiero.GastoTesoreriaService;
import com.franco.dev.service.financiero.MaletinTesoreriaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.financiero.ValeTesoreriaService;
import com.franco.dev.service.rrhh.PrestamoService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Las mutations que reciben {@code claveIdempotencia} (issue #376): la clave llega al servicio, la huella
 * se arma con lo que mandó el cliente —cada campo del pedido la cambia— y el alta es la de siempre, con el
 * usuario de la sesión.
 */
class AltasConClaveGraphQLTest {

    @Mock private TesoreriaSecurityService seg;
    @Mock private RrhhSecurityService segRrhh;
    @Mock private AltaIdempotenteService altaIdempotente;
    @Mock private EntradaVariaService entradaVariaService;
    @Mock private CajaVirtualService cajaVirtualService;
    @Mock private MaletinTesoreriaService maletinService;
    @Mock private GastoTesoreriaService gastoTesoreriaService;
    @Mock private ValeTesoreriaService valeTesoreriaService;
    @Mock private PrestamoService prestamoService;
    @Mock private com.franco.dev.service.financiero.MonedaService monedaService;
    @Mock private com.franco.dev.service.personas.FuncionarioService funcionarioService;
    @Mock private com.franco.dev.service.personas.UsuarioService usuarioService;
    @Mock private com.franco.dev.repository.financiero.EntradaVariaCategoriaRepository categoriaRepository;
    @Mock private com.franco.dev.repository.financiero.FormaPagoRepository formaPagoRepository;

    @InjectMocks private EntradaVariaGraphQL entradaVariaGraphQL;
    @InjectMocks private MaletinTesoreriaGraphQL maletinGraphQL;
    @InjectMocks private PagoProveedorGraphQL pagoProveedorGraphQL;
    @InjectMocks private ValeTesoreriaGraphQL valeGraphQL;
    @InjectMocks private PrestamoGraphQL prestamoGraphQL;

    private AutoCloseable mocks;
    private final Usuario sesion = new Usuario();

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(seg.currentUsuario()).thenReturn(sesion);
        when(segRrhh.currentUsuario()).thenReturn(sesion);
        when(cajaVirtualService.findById(any())).thenReturn(Optional.of(new CajaVirtual()));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ── Operación financiera: la huella es una función pura del input ────────────────────────────

    private static OperacionFinancieraInputWrapper operacion() {
        OperacionFinancieraInputWrapper in = new OperacionFinancieraInputWrapper();
        in.setTipoOperacion(TipoOperacionFinanciera.CAMBIO_DIVISA);
        in.setDescripcion("CAMBIO");
        in.setCategoriaId(1L);
        in.setCajaMayorOrigenId(2L);
        in.setCuentaBancariaOrigenId(3L);
        in.setMonedaOrigenId(4L);
        in.setMontoOrigen(100.0);
        in.setCajaMayorDestinoId(5L);
        in.setCuentaBancariaDestinoId(6L);
        in.setMonedaDestinoId(7L);
        in.setMontoDestino(730000.0);
        in.setCotizacion(7300.0);
        in.setNumeroComprobante("A-1");
        in.setDiferencia(10.0);
        in.setDiferenciaDestinoTipo(com.franco.dev.domain.financiero.enums.DiferenciaDestinoTipo.values()[0]);
        in.setDiferenciaObservacion("OBS");
        return in;
    }

    @Test
    void cada_uno_de_los_16_campos_de_una_operacion_financiera_cambia_la_huella() {
        List<Consumer<OperacionFinancieraInputWrapper>> cambios = new ArrayList<>();
        cambios.add(in -> in.setTipoOperacion(TipoOperacionFinanciera.DEPOSITO_BANCARIO));
        cambios.add(in -> in.setDescripcion("OTRA"));
        cambios.add(in -> in.setCategoriaId(91L));
        cambios.add(in -> in.setCajaMayorOrigenId(92L));
        cambios.add(in -> in.setCuentaBancariaOrigenId(93L));
        cambios.add(in -> in.setMonedaOrigenId(94L));
        cambios.add(in -> in.setMontoOrigen(100.01));
        cambios.add(in -> in.setCajaMayorDestinoId(95L));
        cambios.add(in -> in.setCuentaBancariaDestinoId(96L));
        cambios.add(in -> in.setMonedaDestinoId(97L));
        cambios.add(in -> in.setMontoDestino(730001.0));
        cambios.add(in -> in.setCotizacion(7301.0));
        cambios.add(in -> in.setNumeroComprobante("A-2"));
        cambios.add(in -> in.setDiferencia(11.0));
        cambios.add(in -> in.setDiferenciaDestinoTipo(null));
        cambios.add(in -> in.setDiferenciaObservacion("OTRA OBS"));

        String base = OperacionFinancieraGraphQL.huellaDe(operacion());
        assertEquals(base, OperacionFinancieraGraphQL.huellaDe(operacion()), "el mismo pedido da la misma huella");
        HashSet<String> vistas = new HashSet<>();
        vistas.add(base);
        for (int i = 0; i < cambios.size(); i++) {
            OperacionFinancieraInputWrapper in = operacion();
            cambios.get(i).accept(in);
            assertTrue(vistas.add(OperacionFinancieraGraphQL.huellaDe(in)), "el cambio " + i + " no cambió la huella");
        }
        assertEquals(17, vistas.size());
    }

    @Test
    void un_monto_que_no_es_un_numero_se_rechaza_con_mensaje_en_vez_de_romper_la_huella() {
        OperacionFinancieraInputWrapper in = operacion();
        in.setMontoOrigen(Double.NaN);

        assertThrows(GraphQLException.class, () -> OperacionFinancieraGraphQL.huellaDe(in));
    }

    @Test
    void operacion_financiera_la_clave_la_huella_y_el_usuario_de_la_sesion_llegan_al_servicio() {
        com.franco.dev.service.financiero.OperacionFinancieraService servicio =
                mock(com.franco.dev.service.financiero.OperacionFinancieraService.class);
        OperacionFinancieraGraphQL graphQL = new OperacionFinancieraGraphQL(servicio,
                mock(com.franco.dev.repository.financiero.MovimientoBancarioRepository.class), cajaVirtualService,
                mock(com.franco.dev.repository.financiero.CuentaBancariaRepository.class), monedaService,
                mock(com.franco.dev.repository.financiero.OperacionFinancieraCategoriaRepository.class), seg,
                mock(com.franco.dev.service.financiero.MovimientoBancarioService.class), altaIdempotente);

        graphQL.registrarOperacionFinanciera(operacion(), "clave-of");

        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente).operacionFinanciera(eq("clave-of"), eq(OperacionFinancieraGraphQL.huellaDe(operacion())),
                same(sesion), alta.capture());
        verifyNoInteractions(servicio);
        alta.getValue().get();
        verify(servicio).registrar(any(), same(sesion));
    }

    // ── Las demás: la huella y la clave que le llegan al servicio ───────────────────────────────

    private static EntradaVariaInput entrada() {
        EntradaVariaInput in = new EntradaVariaInput();
        in.setCajaVirtualId(1L);
        in.setMonedaId(2L);
        in.setMonto(1500.0);
        in.setEsIngreso(true);
        in.setDescripcion("VARIOS");
        in.setCategoriaId(3L);
        in.setFormaPagoId(4L);
        in.setNumeroComprobante("");
        return in;
    }

    @Test
    void entrada_varia_la_clave_llega_y_cada_campo_cambia_la_huella() {
        List<Consumer<EntradaVariaInput>> cambios = new ArrayList<>();
        cambios.add(in -> { });
        cambios.add(in -> in.setCajaVirtualId(9L));
        cambios.add(in -> in.setMonedaId(9L));
        cambios.add(in -> in.setMonto(1500.5));
        cambios.add(in -> in.setEsIngreso(false));
        cambios.add(in -> in.setDescripcion("OTRO"));
        cambios.add(in -> in.setCategoriaId(9L));
        cambios.add(in -> in.setFormaPagoId(9L));
        cambios.add(in -> in.setNumeroComprobante("77"));
        for (Consumer<EntradaVariaInput> cambio : cambios) {
            EntradaVariaInput in = entrada();
            cambio.accept(in);
            entradaVariaGraphQL.registrarEntradaVaria(in, "clave-ev");
        }
        entradaVariaGraphQL.registrarEntradaVaria(entrada(), "clave-ev");   // el reintento

        ArgumentCaptor<String> huellas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente, times(10)).entradaVaria(eq("clave-ev"), huellas.capture(), same(sesion), alta.capture());
        assertEquals(9, new HashSet<>(huellas.getAllValues()).size(), "cada campo tiene que cambiar la huella");
        assertEquals(huellas.getAllValues().get(0), huellas.getAllValues().get(9), "el reintento da la misma huella");

        verifyNoInteractions(entradaVariaService);   // el alta corre solo si la clave es nueva
        alta.getAllValues().get(0).get();
        verify(entradaVariaService).registrar(any(), same(sesion));
    }

    @Test
    void maletin_el_ingreso_y_el_egreso_pasan_la_clave_y_no_comparten_operacion() {
        maletinGraphQL.egresarMaletinCajaMayor(1L, 2L, 3L, 500.0, "DESPACHO", "clave-m");
        maletinGraphQL.egresarMaletinCajaMayor(1L, 2L, 3L, 500.00, "DESPACHO", "clave-m");
        maletinGraphQL.egresarMaletinCajaMayor(1L, 2L, 3L, 501.0, "DESPACHO", "clave-m");
        maletinGraphQL.egresarMaletinCajaMayor(1L, 8L, 3L, 500.0, "DESPACHO", "clave-m");
        maletinGraphQL.egresarMaletinCajaMayor(1L, 2L, 3L, 500.0, "OTRO", "clave-m");
        maletinGraphQL.ingresarMaletinCajaMayor(1L, 2L, 3L, 500.0, "DESPACHO", "clave-i");

        ArgumentCaptor<String> huellas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente, times(5)).maletin(eq(false), eq("clave-m"), huellas.capture(), same(sesion), alta.capture());
        verify(altaIdempotente).maletin(eq(true), eq("clave-i"), any(), same(sesion), any());
        List<String> h = huellas.getAllValues();
        assertEquals(h.get(0), h.get(1));
        assertEquals(4, new HashSet<>(h).size());

        alta.getAllValues().get(0).get();
        verify(maletinService).egresarMaletin(eq(1L), eq(2L), eq(3L), eq(BigDecimal.valueOf(500.0)), eq("DESPACHO"), same(sesion));
        verify(maletinService, never()).ingresarMaletin(any(), any(), any(), any(), any(), any());
    }

    private static GastoParaPagoWrapper gasto() {
        GastoParaPagoWrapper in = new GastoParaPagoWrapper();
        in.setTipoGastoId(1L);
        in.setDescripcion("FLETE");
        in.setMonedaId(2L);
        in.setMonto(250000.0);
        in.setBeneficiarioProveedorId(3L);
        return in;
    }

    @Test
    void gasto_la_clave_llega_y_cada_campo_cambia_la_huella() {
        List<Consumer<GastoParaPagoWrapper>> cambios = new ArrayList<>();
        cambios.add(in -> { });
        cambios.add(in -> in.setTipoGastoId(9L));
        cambios.add(in -> in.setDescripcion("OTRO"));
        cambios.add(in -> in.setMonedaId(9L));
        cambios.add(in -> in.setMonto(250001.0));
        cambios.add(in -> in.setBeneficiarioProveedorId(9L));
        cambios.add(in -> in.setBeneficiarioPersonaId(9L));
        cambios.add(in -> in.setSucursalId(9L));
        for (Consumer<GastoParaPagoWrapper> cambio : cambios) {
            GastoParaPagoWrapper in = gasto();
            cambio.accept(in);
            pagoProveedorGraphQL.crearGastoParaPago(in, "clave-g");
        }

        ArgumentCaptor<String> huellas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente, times(8)).gastoParaPago(eq("clave-g"), huellas.capture(), same(sesion), alta.capture());
        assertEquals(8, new HashSet<>(huellas.getAllValues()).size());

        alta.getAllValues().get(0).get();
        verify(gastoTesoreriaService).crearGastoParaPago(eq(1L), eq("FLETE"), eq(2L), eq(250000.0), eq(3L),
                any(), any(), any(), same(sesion));
    }

    @Test
    void vale_la_clave_llega_y_cada_campo_cambia_la_huella() {
        List<Consumer<ValeParaPagoWrapper>> cambios = new ArrayList<>();
        cambios.add(in -> { });
        cambios.add(in -> in.setFuncionarioId(9L));
        cambios.add(in -> in.setMotivoId(9L));
        cambios.add(in -> in.setMonedaId(9L));
        cambios.add(in -> in.setMonto(100001.0));
        cambios.add(in -> in.setEsAdelanto(false));
        cambios.add(in -> in.setObservacion("OTRA"));
        for (Consumer<ValeParaPagoWrapper> cambio : cambios) {
            ValeParaPagoWrapper in = new ValeParaPagoWrapper();
            in.setFuncionarioId(1L);
            in.setMonedaId(2L);
            in.setMonto(100000.0);
            in.setEsAdelanto(true);
            cambio.accept(in);
            valeGraphQL.crearValeParaPago(in, "clave-v");
        }

        ArgumentCaptor<String> huellas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente, times(7)).valeParaPago(eq("clave-v"), huellas.capture(), same(sesion), alta.capture());
        assertEquals(7, new HashSet<>(huellas.getAllValues()).size());

        alta.getAllValues().get(0).get();
        verify(valeTesoreriaService).crearValeParaPago(eq(1L), any(), eq(2L), eq(BigDecimal.valueOf(100000.0)),
                eq(true), any(), same(sesion));
    }

    @Test
    void prestamo_la_clave_llega_y_cada_campo_y_la_caja_cambian_la_huella() {
        List<Consumer<PrestamoInput>> cambios = new ArrayList<>();
        cambios.add(in -> { });
        cambios.add(in -> in.setFuncionarioId(9L));
        cambios.add(in -> in.setDescripcion("OTRO"));
        cambios.add(in -> in.setMontoTotal(new BigDecimal("1000001")));
        cambios.add(in -> in.setMonedaId(9L));
        cambios.add(in -> in.setCantidadCuotas(11));
        cambios.add(in -> in.setObservacion("OTRA"));
        for (Consumer<PrestamoInput> cambio : cambios) {
            PrestamoInput in = prestamo();
            cambio.accept(in);
            prestamoGraphQL.crearPrestamo(in, 5L, "clave-p");
        }
        prestamoGraphQL.crearPrestamo(prestamo(), 6L, "clave-p");   // otra caja

        ArgumentCaptor<String> huellas = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Supplier> alta = ArgumentCaptor.forClass(Supplier.class);
        verify(altaIdempotente, times(8)).prestamo(eq("clave-p"), huellas.capture(), same(sesion), alta.capture());
        assertEquals(8, new HashSet<>(huellas.getAllValues()).size());

        verifyNoInteractions(prestamoService);   // sin clave nueva no se desembolsa
        alta.getAllValues().get(0).get();
        verify(prestamoService).crearConDesembolso(any(), eq(5L));
    }

    private static PrestamoInput prestamo() {
        PrestamoInput in = new PrestamoInput();
        in.setFuncionarioId(1L);
        in.setDescripcion("PRESTAMO");
        in.setMontoTotal(new BigDecimal("1000000"));
        in.setMonedaId(2L);
        in.setCantidadCuotas(10);
        return in;
    }
}
