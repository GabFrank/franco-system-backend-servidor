package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.MovimientoBancarioTipo;
import com.franco.dev.domain.financiero.enums.MovimientoClienteTipo;
import com.franco.dev.domain.personas.Cliente;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** CPC (F5): cobro parcial/total por banco, doble ledger, tolerancia, finalización. */
class CobroCreditoServiceTest {

    private VentaCreditoService ventaCreditoService;
    private VentaCreditoCuotaService ventaCreditoCuotaService;
    private BancoLedgerService bancoLedgerService;
    private ClienteCuentaService clienteCuentaService;
    private CobroCreditoService service;

    private VentaCredito vc;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        ventaCreditoService = mock(VentaCreditoService.class);
        ventaCreditoCuotaService = mock(VentaCreditoCuotaService.class);
        bancoLedgerService = mock(BancoLedgerService.class);
        clienteCuentaService = mock(ClienteCuentaService.class);
        service = new CobroCreditoService(ventaCreditoService, ventaCreditoCuotaService, bancoLedgerService, clienteCuentaService);

        cliente = new Cliente(); cliente.setId(3L);
        vc = new VentaCredito(); vc.setId(1L); vc.setCliente(cliente);

        MovimientoBancario mb = new MovimientoBancario(); mb.setId(555L);
        when(bancoLedgerService.registrar(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(mb);
        when(ventaCreditoCuotaService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private VentaCreditoCuota cuota(double valor, BigDecimal cobrado) {
        VentaCreditoCuota c = new VentaCreditoCuota();
        c.setId(10L); c.setValor(valor); c.setMontoCobrado(cobrado); c.setActivo(true);
        c.setVentaCredito(vc);
        return c;
    }

    @Test
    void cobro_total_marca_cobrado_acredita_banco_y_paga_cliente() {
        VentaCreditoCuota c = cuota(100000, BigDecimal.ZERO);
        when(ventaCreditoService.cuota(10L, 2L)).thenReturn(c);
        when(ventaCreditoService.cuotasDeVenta(vc)).thenReturn(Collections.singletonList(c));

        service.cobrarCuotaBanco(10L, 2L, new BigDecimal("100000"), 9L, new BigDecimal("100000"), null, null);

        assertEquals("COBRADO", c.getEstadoCobro());
        verify(bancoLedgerService).registrar(eq(9L), eq(MovimientoBancarioTipo.ENTRADA_MANUAL), eq(new BigDecimal("100000")), any(), any(), any(), any());
        verify(clienteCuentaService).registrar(eq(3L), eq(MovimientoClienteTipo.PAGO), eq(new BigDecimal("100000")), any(), any());
        verify(ventaCreditoService).finalizarPorCobro(vc); // todas cobradas
    }

    @Test
    void cobro_parcial_marca_parcial_sin_finalizar() {
        VentaCreditoCuota c = cuota(100000, BigDecimal.ZERO);
        when(ventaCreditoService.cuota(10L, 2L)).thenReturn(c);

        service.cobrarCuotaBanco(10L, 2L, new BigDecimal("40000"), 9L, new BigDecimal("40000"), null, null);

        assertEquals("PARCIAL", c.getEstadoCobro());
        assertEquals(new BigDecimal("40000"), c.getMontoCobrado());
        verify(ventaCreditoService, never()).finalizarPorCobro(any());
    }

    @Test
    void monto_mayor_al_saldo_falla() {
        VentaCreditoCuota c = cuota(100000, new BigDecimal("60000")); // restante 40000
        when(ventaCreditoService.cuota(10L, 2L)).thenReturn(c);

        assertThrows(GraphQLException.class,
                () -> service.cobrarCuotaBanco(10L, 2L, new BigDecimal("50000"), 9L, new BigDecimal("50000"), null, null));
        verifyNoInteractions(bancoLedgerService);
    }

    @Test
    void cuota_ya_cobrada_falla() {
        VentaCreditoCuota c = cuota(100000, new BigDecimal("100000"));
        c.setEstadoCobro("COBRADO");
        when(ventaCreditoService.cuota(10L, 2L)).thenReturn(c);

        assertThrows(GraphQLException.class,
                () -> service.cobrarCuotaBanco(10L, 2L, new BigDecimal("1"), 9L, new BigDecimal("1"), null, null));
    }
}
