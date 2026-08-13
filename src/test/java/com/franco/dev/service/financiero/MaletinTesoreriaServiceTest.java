package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.repository.financiero.MonedaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Puente maletín ↔ caja mayor: valor desde conteo de cierre + posteo de ingreso/egreso. */
class MaletinTesoreriaServiceTest {

    private MaletinService maletinService;
    private PdvCajaService pdvCajaService;
    private ConteoMonedaService conteoMonedaService;
    private CajaVirtualRepository cajaVirtualRepository;
    private MonedaRepository monedaRepository;
    private TesoreriaService tesoreriaService;
    private MaletinTesoreriaService service;

    @BeforeEach
    void setUp() {
        maletinService = mock(MaletinService.class);
        pdvCajaService = mock(PdvCajaService.class);
        conteoMonedaService = mock(ConteoMonedaService.class);
        cajaVirtualRepository = mock(CajaVirtualRepository.class);
        monedaRepository = mock(MonedaRepository.class);
        tesoreriaService = mock(TesoreriaService.class);
        service = new MaletinTesoreriaService(maletinService, pdvCajaService, conteoMonedaService,
                cajaVirtualRepository, monedaRepository, tesoreriaService);
    }

    private Moneda moneda(long id) { Moneda m = new Moneda(); m.setId(id); m.setDenominacion("GUARANI"); return m; }
    private MonedaBilletes billete(Moneda m, double valor) {
        MonedaBilletes mb = new MonedaBilletes(); mb.setMoneda(m); mb.setValor(valor); return mb;
    }
    private ConteoMoneda linea(MonedaBilletes mb, double cantidad) {
        ConteoMoneda cm = new ConteoMoneda(); cm.setMonedaBilletes(mb); cm.setCantidad(cantidad); return cm;
    }

    @Test
    void valor_maletin_suma_por_moneda_del_ultimo_cierre() {
        Moneda gs = moneda(1);
        PdvCaja caja = new PdvCaja();
        caja.setSucursalId(3L);
        Conteo cierre = new Conteo(); cierre.setId(99L);
        caja.setConteoCierre(cierre);
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(caja);
        // 10 billetes de 100.000 + 3 de 50.000 = 1.150.000
        when(conteoMonedaService.findByConteoId(99L, 3L)).thenReturn(List.of(
                linea(billete(gs, 100000d), 10d),
                linea(billete(gs, 50000d), 3d)));

        List<MaletinTesoreriaService.ValorMaletinItem> items = service.valorMaletin(5L);

        assertEquals(1, items.size());
        assertEquals(0, new BigDecimal("1150000").compareTo(items.get(0).getTotal()));
    }

    @Test
    void valor_maletin_sin_cierre_devuelve_vacio() {
        PdvCaja caja = new PdvCaja(); // sin conteoCierre
        when(pdvCajaService.findLastByMaletinId(5L)).thenReturn(caja);
        assertTrue(service.valorMaletin(5L).isEmpty());
    }

    @Test
    void ingresar_maletin_postea_ingreso_etiquetado() {
        Maletin maletin = new Maletin(); maletin.setId(5L); maletin.setDescripcion("MALETIN A");
        when(maletinService.findById(5L)).thenReturn(Optional.of(maletin));
        when(cajaVirtualRepository.findById(1L)).thenReturn(Optional.of(new CajaVirtual()));
        when(monedaRepository.findById(2L)).thenReturn(Optional.of(moneda(2)));

        service.ingresarMaletin(1L, 5L, 2L, new BigDecimal("1150000"), "llegada", null);

        ArgumentCaptor<MovimientoCajaVirtual> cap = ArgumentCaptor.forClass(MovimientoCajaVirtual.class);
        verify(tesoreriaService).registrar(cap.capture());
        MovimientoCajaVirtual m = cap.getValue();
        assertEquals(CajaVirtualTipoMovimiento.INGRESO, m.getTipoMovimiento());
        assertEquals(OrigenMovimientoTipo.MALETIN, m.getOrigenTipo());
        assertEquals(5L, m.getReferenciaId());
        assertTrue(m.getDescripcion().startsWith("INGRESO MALETIN MALETIN A"));
    }

    @Test
    void egresar_maletin_con_monto_invalido_falla() {
        assertThrows(graphql.GraphQLException.class,
                () -> service.egresarMaletin(1L, 5L, 2L, BigDecimal.ZERO, null, null));
    }
}
