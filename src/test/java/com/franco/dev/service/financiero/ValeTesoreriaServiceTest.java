package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.ValeRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.rrhh.MotivoValeService;
import com.franco.dev.service.rrhh.dto.ValePendienteDto;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pago de vales desde tesorería: puente vale → SolicitudPago(RRHH) + delegación al motor de CPP.
 * Lo que se protege acá es que un vale se pague entero y una sola vez.
 */
class ValeTesoreriaServiceTest {

    private ValeRepository valeRepository;
    private SolicitudPagoService solicitudPagoService;
    private PagoProveedorService pagoProveedorService;
    private FuncionarioService funcionarioService;
    private MotivoValeService motivoValeService;
    private MonedaService monedaService;
    private ValeTesoreriaService service;

    @BeforeEach
    void setUp() {
        valeRepository = mock(ValeRepository.class);
        solicitudPagoService = mock(SolicitudPagoService.class);
        pagoProveedorService = mock(PagoProveedorService.class);
        funcionarioService = mock(FuncionarioService.class);
        motivoValeService = mock(MotivoValeService.class);
        monedaService = mock(MonedaService.class);
        service = new ValeTesoreriaService(valeRepository, solicitudPagoService, pagoProveedorService,
                funcionarioService, motivoValeService, monedaService);
        when(valeRepository.save(any(Vale.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Moneda moneda() { Moneda m = new Moneda(); m.setId(1L); m.setDenominacion("GUARANI"); return m; }

    private Funcionario funcionario(String nombre) {
        Persona p = new Persona(); p.setId(9L); p.setNombre(nombre);
        Funcionario f = new Funcionario(); f.setId(5L); f.setPersona(p);
        return f;
    }

    private Vale vale(Long id, ValeEstado estado, double monto) {
        Vale v = new Vale();
        v.setId(id); v.setEstado(estado); v.setMonto(BigDecimal.valueOf(monto));
        v.setMoneda(moneda()); v.setFuncionario(funcionario("JUAN PEREZ"));
        return v;
    }

    private ValeTesoreriaService.ValeConLineas pago(Long valeId, double montoLinea) {
        PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
        l.setMonto(BigDecimal.valueOf(montoLinea));
        l.setMontoSolicitud(BigDecimal.valueOf(montoLinea));
        ValeTesoreriaService.ValeConLineas p = new ValeTesoreriaService.ValeConLineas();
        p.setValeId(valeId); p.setLineas(List.of(l));
        return p;
    }

    private SolicitudPago solicitud(Long id) {
        SolicitudPago sp = new SolicitudPago();
        sp.setId(id); sp.setTipo(TipoSolicitudPago.RRHH); sp.setEstado(SolicitudPagoEstado.SOLICITADO);
        return sp;
    }

    @Test
    void listaSoloValesSolicitadosConSuSaldo() {
        when(valeRepository.findByEstadoOrderByFechaDescIdDesc(ValeEstado.SOLICITADO))
                .thenReturn(List.of(vale(1L, ValeEstado.SOLICITADO, 1_000_000)));

        List<ValePendienteDto> filas = service.listarValesPendientes();

        assertEquals(1, filas.size());
        assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(filas.get(0).getSaldoPendiente()));
        assertEquals("JUAN PEREZ", filas.get(0).getFuncionarioNombre());
    }

    @Test
    void creaElValePendienteDePagoSinMoverPlata() {
        when(funcionarioService.findById(5L)).thenReturn(Optional.of(funcionario("JUAN PEREZ")));
        when(monedaService.findById(1L)).thenReturn(Optional.of(moneda()));
        when(solicitudPagoService.crearSolicitudVale(any(), anyDouble(), anyString(), any()))
                .thenReturn(solicitud(77L));

        Vale creado = service.crearValeParaPago(5L, null, 1L, BigDecimal.valueOf(500_000),
                true, "adelanto", new Usuario());

        // SOLICITADO = registrado y todavía sin entregar la plata.
        assertEquals(ValeEstado.SOLICITADO, creado.getEstado());
        assertEquals(77L, creado.getSolicitudPagoId());
        assertEquals("ADELANTO", creado.getObservacion());
        // No se postea ningún movimiento de caja en el alta.
        verifyNoInteractions(pagoProveedorService);
    }

    @Test
    void rechazaPagarUnValeQueNoEstaPendiente() {
        when(valeRepository.findById(1L)).thenReturn(Optional.of(vale(1L, ValeEstado.CONFIRMADO, 1_000_000)));

        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.pagarValesMixto(List.of(pago(1L, 1_000_000)), new Usuario()));

        assertTrue(ex.getMessage().contains("no esta pendiente"));
        verifyNoInteractions(pagoProveedorService);
    }

    @Test
    void rechazaElPagoParcialDeUnVale() {
        // La liquidación descuenta el monto total del vale: entregar de menos dejaría plata
        // fuera de caja que nunca se recupera del sueldo.
        when(valeRepository.findById(1L)).thenReturn(Optional.of(vale(1L, ValeEstado.SOLICITADO, 1_000_000)));

        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.pagarValesMixto(List.of(pago(1L, 400_000)), new Usuario()));

        assertTrue(ex.getMessage().contains("entero"));
        verifyNoInteractions(pagoProveedorService);
    }

    @Test
    void creaLaObligacionDePagoSiElValeNoLaTiene() {
        // Caso real: los vales que crea el mobile nacen SOLICITADO y sin SolicitudPago.
        Vale v = vale(1L, ValeEstado.SOLICITADO, 1_000_000);
        when(valeRepository.findById(1L)).thenReturn(Optional.of(v));
        when(solicitudPagoService.crearSolicitudVale(any(), anyDouble(), anyString(), any()))
                .thenReturn(solicitud(88L));

        service.pagarValesMixto(List.of(pago(1L, 1_000_000)), new Usuario());

        assertEquals(88L, v.getSolicitudPagoId());
        verify(pagoProveedorService).pagarLoteMixto(argThat(lote ->
                lote.size() == 1 && lote.get(0).getSolicitudId().equals(88L)), any());
    }

    @Test
    void reutilizaLaObligacionDePagoExistenteEnLugarDeDuplicarla() {
        Vale v = vale(1L, ValeEstado.SOLICITADO, 1_000_000);
        v.setSolicitudPagoId(88L);
        when(valeRepository.findById(1L)).thenReturn(Optional.of(v));
        when(solicitudPagoService.findById(88L)).thenReturn(Optional.of(solicitud(88L)));

        service.pagarValesMixto(List.of(pago(1L, 1_000_000)), new Usuario());

        verify(solicitudPagoService, never()).crearSolicitudVale(any(), anyDouble(), anyString(), any());
        verify(pagoProveedorService).pagarLoteMixto(argThat(lote ->
                lote.get(0).getSolicitudId().equals(88L)), any());
    }
}
