package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.enums.*;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CPP (F6): pago simple/mixto, transición de estado, ajuste Fx, tope por saldo. Sin ledger de proveedor. */
class PagoProveedorServiceTest {

    private SolicitudPagoService solicitudPagoService;
    private com.franco.dev.service.operaciones.PagoService pagoService;
    private TesoreriaService tesoreriaService;
    private BancoLedgerService bancoLedgerService;
    private CajaVirtualRepository cajaVirtualRepository;
    private MonedaRepository monedaRepository;
    private ChequeGestionService chequeGestionService;
    private com.franco.dev.repository.financiero.ChequeraRepository chequeraRepo;
    private PagoProveedorService service;

    private SolicitudPago sp;

    @BeforeEach
    void setUp() {
        solicitudPagoService = mock(SolicitudPagoService.class);
        pagoService = mock(com.franco.dev.service.operaciones.PagoService.class);
        tesoreriaService = mock(TesoreriaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        cajaVirtualRepository = mock(CajaVirtualRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository detalleRepo =
                mock(com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository.class);
        when(detalleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        com.franco.dev.repository.financiero.MovimientoBancarioRepository movBancarioRepo =
                mock(com.franco.dev.repository.financiero.MovimientoBancarioRepository.class);
        chequeGestionService = mock(ChequeGestionService.class);
        chequeraRepo = mock(com.franco.dev.repository.financiero.ChequeraRepository.class);
        com.franco.dev.domain.operaciones.Pago pagoEvento = new com.franco.dev.domain.operaciones.Pago();
        pagoEvento.setId(500L);
        when(pagoService.save(any())).thenReturn(pagoEvento);
        service = new PagoProveedorService(solicitudPagoService, pagoService, tesoreriaService, bancoLedgerService,
                chequeGestionService, chequeraRepo, cajaVirtualRepository, monedaRepository, detalleRepo, movBancarioRepo);

        com.franco.dev.domain.personas.Persona persona = new com.franco.dev.domain.personas.Persona(); persona.setNombre("PROV X");
        Proveedor prov = new Proveedor(); prov.setId(7L); prov.setPersona(persona);
        sp = new SolicitudPago(); sp.setId(1L); sp.setMontoTotal(100000.0);
        sp.setMontoPagado(BigDecimal.ZERO); sp.setEstado(SolicitudPagoEstado.PENDIENTE); sp.setProveedor(prov);

        com.franco.dev.repository.operaciones.SolicitudPagoRepository spRepo =
                mock(com.franco.dev.repository.operaciones.SolicitudPagoRepository.class);
        when(solicitudPagoService.getRepository()).thenReturn(spRepo);
        when(spRepo.lockById(1L)).thenReturn(Optional.of(sp));
        when(spRepo.findById(1L)).thenReturn(Optional.of(sp));
        when(solicitudPagoService.save(any())).thenAnswer(i -> i.getArgument(0));
        com.franco.dev.domain.financiero.MovimientoCajaVirtual mcv = new com.franco.dev.domain.financiero.MovimientoCajaVirtual();
        mcv.setId(888L);
        when(tesoreriaService.registrar(any())).thenReturn(mcv);
        com.franco.dev.domain.financiero.MovimientoBancario mb = new com.franco.dev.domain.financiero.MovimientoBancario();
        mb.setId(999L);
        when(bancoLedgerService.registrar(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(mb);
        when(cajaVirtualRepository.findById(anyLong())).thenReturn(Optional.of(new com.franco.dev.domain.financiero.CajaVirtual()));
    }

    private PagoProveedorService.LineaPago linea(FuentePago fuente, double monto) {
        PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
        l.setFuente(fuente); l.setMonto(BigDecimal.valueOf(monto));
        l.setCajaVirtualId(9L); l.setCuentaBancariaId(4L);
        return l;
    }

    @Test
    void pago_total_caja_concluye() {
        service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 100000)), null);
        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        assertEquals(0, sp.getMontoPagado().compareTo(new BigDecimal("100000")));
        verify(tesoreriaService).registrar(any());
    }

    @Test
    void pago_con_ajuste_descuento_concluye_sin_mover_efectivo_extra() {
        // Efectivo 99.872 + ajuste descuento 128 = deuda 100.000 → CONCLUIDO, el ajuste no postea movimiento.
        PagoProveedorService.LineaPago fisica = linea(FuentePago.CAJA_MAYOR, 99872);
        fisica.setMontoSolicitud(new BigDecimal("99872"));
        PagoProveedorService.LineaPago ajuste = new PagoProveedorService.LineaPago();
        ajuste.setFuente(FuentePago.AJUSTE); ajuste.setDescuento(true);
        ajuste.setMonto(BigDecimal.ZERO); ajuste.setMontoSolicitud(new BigDecimal("128"));
        service.pagar(1L, Arrays.asList(fisica, ajuste), null);
        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        assertEquals(0, sp.getMontoPagado().compareTo(new BigDecimal("100000")));
        verify(tesoreriaService, times(1)).registrar(any()); // solo la línea física
    }

    @Test
    void pago_parcial_deja_parcial() {
        service.pagar(1L, Collections.singletonList(linea(FuentePago.CUENTA_BANCARIA, 40000)), null);
        assertEquals(SolicitudPagoEstado.PARCIAL, sp.getEstado());
        assertEquals(0, sp.getMontoPagado().compareTo(new BigDecimal("40000")));
        verify(bancoLedgerService).registrar(eq(4L), eq(MovimientoBancarioTipo.SALIDA_MANUAL),
                argThat(v -> v.compareTo(new BigDecimal("40000")) == 0), any(), any(), any(), any());
    }

    @Test
    void pago_mixto_dos_lineas_suma_ambas() {
        service.pagar(1L, Arrays.asList(linea(FuentePago.CAJA_MAYOR, 60000), linea(FuentePago.CUENTA_BANCARIA, 40000)), null);
        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        verify(tesoreriaService).registrar(any());
        verify(bancoLedgerService).registrar(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pago_dos_solicitudes_misma_fuente_consolida_un_movimiento() {
        SolicitudPago sp2 = new SolicitudPago(); sp2.setId(2L); sp2.setMontoTotal(50000.0);
        sp2.setMontoPagado(BigDecimal.ZERO); sp2.setEstado(SolicitudPagoEstado.PENDIENTE); sp2.setProveedor(sp.getProveedor());
        com.franco.dev.repository.operaciones.SolicitudPagoRepository spRepo = solicitudPagoService.getRepository();
        when(spRepo.lockById(2L)).thenReturn(Optional.of(sp2));

        PagoProveedorService.SolicitudConLineas s1 = new PagoProveedorService.SolicitudConLineas();
        s1.setSolicitudId(1L); s1.setLineas(Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 100000)));
        PagoProveedorService.SolicitudConLineas s2 = new PagoProveedorService.SolicitudConLineas();
        s2.setSolicitudId(2L); s2.setLineas(Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 50000)));

        service.pagarLoteMixto(Arrays.asList(s1, s2), null);

        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp2.getEstado());
        verify(tesoreriaService, times(1)).registrar(any()); // ambas notas → un solo movimiento consolidado
    }

    @Test
    void pago_con_cheque_emite_cheque_y_liga_detalle() {
        // Una línea CHEQUE emite 1 cheque (vía ChequeGestionService) y no se consolida como banco.
        com.franco.dev.domain.financiero.Chequera chq = new com.franco.dev.domain.financiero.Chequera();
        chq.setId(3L);
        when(chequeraRepo.findById(3L)).thenReturn(Optional.of(chq));
        com.franco.dev.domain.financiero.Cheque emitido = new com.franco.dev.domain.financiero.Cheque();
        emitido.setId(77L);
        when(chequeGestionService.emitir(any(), any())).thenReturn(emitido);

        PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
        l.setFuente(FuentePago.CHEQUE); l.setChequeraId(3L); l.setDiferido(true);
        l.setMonto(BigDecimal.valueOf(100000)); l.setMontoSolicitud(BigDecimal.valueOf(100000));

        service.pagar(1L, Collections.singletonList(l), null);

        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        verify(chequeGestionService, times(1)).emitir(any(), any());
        verify(tesoreriaService, never()).registrar(any());          // no toca caja
        verify(bancoLedgerService, never()).registrar(anyLong(), any(), any(), any(), any(), any(), any()); // el banco lo toca emitir, no el pago
    }

    @Test
    void pago_mayor_al_saldo_falla() {
        assertThrows(GraphQLException.class,
                () -> service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 150000)), null));
    }

    @Test
    void solicitud_repetida_en_el_evento_falla() {
        PagoProveedorService.SolicitudConLineas s1 = new PagoProveedorService.SolicitudConLineas();
        s1.setSolicitudId(1L); s1.setLineas(Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 50000)));
        PagoProveedorService.SolicitudConLineas s2 = new PagoProveedorService.SolicitudConLineas();
        s2.setSolicitudId(1L); s2.setLineas(Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 50000)));
        assertThrows(GraphQLException.class, () -> service.pagarLoteMixto(Arrays.asList(s1, s2), null));
    }

    @Test
    void linea_con_monto_inconsistente_falla() {
        // monto 40000 × cotiz 1 = 40000, pero montoSolicitud dice 100000 → inconsistente.
        PagoProveedorService.LineaPago l = linea(FuentePago.CAJA_MAYOR, 40000);
        l.setCotizacion(BigDecimal.ONE); l.setMontoSolicitud(BigDecimal.valueOf(100000));
        assertThrows(GraphQLException.class, () -> service.pagar(1L, Collections.singletonList(l), null));
    }

    @Test
    void solicitud_concluida_falla() {
        sp.setEstado(SolicitudPagoEstado.CONCLUIDO);
        assertThrows(GraphQLException.class,
                () -> service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 1)), null));
    }
}
