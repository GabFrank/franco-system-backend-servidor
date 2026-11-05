package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Un gasto con montos nulos (llega asi por replica) y un retiro sin moneda cuentan como 0 en el balance. */
class PdvCajaServiceMontosNulosTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private ConteoMonedaService conteoMonedaService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private GastoService gastoService;
    @Mock private com.franco.dev.service.empresarial.SucursalService sucursalService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private com.franco.dev.service.operaciones.VentaService ventaService;
    @InjectMocks private PdvCajaService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(conteoMonedaService.findByConteoId(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        when(ventaService.getRepository().sumarioVentasPorCajaAndSurusal(anyLong(), anyLong())).thenReturn(Collections.emptyList());
    }

    @Test
    void gastoConMontosNulosYRetiroSinMonedaCuentanComoCero() {
        PdvCaja caja = new PdvCaja();
        caja.setId(1185L);
        caja.setSucursalId(24L);
        Conteo apertura = new Conteo();
        apertura.setId(1L);
        caja.setConteoApertura(apertura);
        Gasto conMonto = new Gasto();
        conMonto.setRetiroGs(30_000.0);
        when(gastoService.findByCajaId(1185L, 24L)).thenReturn(Arrays.asList(new Gasto(), conMonto));
        when(retiroDetalleService.findByCajId(1185L, 24L)).thenReturn(Collections.singletonList(new RetiroDetalle()));

        PdvCajaBalanceDto b = service.generarBalance(caja);

        assertEquals(30_000.0, b.getTotalGastoGs(), 0.001);
        assertEquals(0.0, b.getTotalGastoRs(), 0.001);
        assertEquals(0.0, b.getTotalRetiroGs(), 0.001);
    }

    @Test
    void unGastoNuevoSeGuardaConLosMontosSinMonedaEnCero() throws Exception {
        Gasto g = new Gasto();
        g.setRetiroGs(5_000.0);
        java.lang.reflect.Method m = Gasto.class.getDeclaredMethod("montosEnCero");
        assertNotNull(m.getAnnotation(javax.persistence.PrePersist.class));
        assertNotNull(m.getAnnotation(javax.persistence.PreUpdate.class));
        m.setAccessible(true);
        m.invoke(g);
        assertEquals(5_000.0, g.getRetiroGs());
        assertEquals(0.0, g.getRetiroRs());
        assertEquals(0.0, g.getRetiroDs());
        assertEquals(0.0, g.getVueltoGs());
        assertEquals(0.0, g.getVueltoRs());
        assertEquals(0.0, g.getVueltoDs());
    }
}
