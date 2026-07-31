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

/** CPP (F6): pago simple/mixto, transición de estado, doble ledger, tope por saldo. */
class PagoProveedorServiceTest {

    private SolicitudPagoService solicitudPagoService;
    private TesoreriaService tesoreriaService;
    private BancoLedgerService bancoLedgerService;
    private ProveedorCuentaService proveedorCuentaService;
    private CajaVirtualRepository cajaVirtualRepository;
    private MonedaRepository monedaRepository;
    private PagoProveedorService service;

    private SolicitudPago sp;

    @BeforeEach
    void setUp() {
        solicitudPagoService = mock(SolicitudPagoService.class);
        tesoreriaService = mock(TesoreriaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        proveedorCuentaService = mock(ProveedorCuentaService.class);
        cajaVirtualRepository = mock(CajaVirtualRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        service = new PagoProveedorService(solicitudPagoService, tesoreriaService, bancoLedgerService,
                proveedorCuentaService, cajaVirtualRepository, monedaRepository);

        Proveedor prov = new Proveedor(); prov.setId(7L);
        sp = new SolicitudPago(); sp.setId(1L); sp.setMontoTotal(100000.0);
        sp.setMontoPagado(BigDecimal.ZERO); sp.setEstado(SolicitudPagoEstado.PENDIENTE); sp.setProveedor(prov);

        com.franco.dev.repository.operaciones.SolicitudPagoRepository spRepo =
                mock(com.franco.dev.repository.operaciones.SolicitudPagoRepository.class);
        when(solicitudPagoService.getRepository()).thenReturn(spRepo);
        when(spRepo.lockById(1L)).thenReturn(Optional.of(sp));
        when(solicitudPagoService.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cajaVirtualRepository.findById(anyLong())).thenReturn(Optional.of(new com.franco.dev.domain.financiero.CajaVirtual()));
    }

    private PagoProveedorService.LineaPago linea(FuentePago fuente, double monto) {
        PagoProveedorService.LineaPago l = new PagoProveedorService.LineaPago();
        l.setFuente(fuente); l.setMonto(BigDecimal.valueOf(monto));
        l.setCajaVirtualId(9L); l.setCuentaBancariaId(4L);
        return l;
    }

    @Test
    void pago_total_caja_concluye_y_paga_proveedor() {
        service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 100000)), null);
        assertEquals(SolicitudPagoEstado.CONCLUIDO, sp.getEstado());
        verify(tesoreriaService).registrar(any());
        verify(proveedorCuentaService).registrar(eq(7L), eq(MovimientoProveedorTipo.PAGO),
                argThat(v -> v.compareTo(new BigDecimal("100000")) == 0), any(), any());
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
    void pago_mayor_al_saldo_falla() {
        assertThrows(GraphQLException.class,
                () -> service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 150000)), null));
    }

    @Test
    void solicitud_concluida_falla() {
        sp.setEstado(SolicitudPagoEstado.CONCLUIDO);
        assertThrows(GraphQLException.class,
                () -> service.pagar(1L, Collections.singletonList(linea(FuentePago.CAJA_MAYOR, 1)), null));
    }
}
