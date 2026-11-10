package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** gasto.cancelado desde tesoreria (srv #587): solo al pasar de no cancelado a cancelado. */
class GastoTesoreriaEventosTest {

    private final GastoRepository repo = mock(GastoRepository.class);
    private final GastoService gastoService = mock(GastoService.class);
    private final GastoTesoreriaService svc = new GastoTesoreriaService(mock(SolicitudPagoService.class),
            mock(TipoGastoService.class), mock(MonedaService.class), mock(ProveedorService.class), repo, gastoService);

    private static SolicitudPago sp(TipoSolicitudPago tipo, String pagado) {
        SolicitudPago s = new SolicitudPago();
        s.setId(20L);
        s.setTipo(tipo);
        s.setMontoPagado(pagado == null ? null : new BigDecimal(pagado));
        return s;
    }

    private Gasto gasto(Boolean cancelado) {
        Gasto g = new Gasto();
        g.setId(11L);
        g.setSucursalId(0L);
        g.setCancelado(cancelado);
        when(repo.findFirstBySolicitudPagoId(20L)).thenReturn(g);
        return g;
    }

    @Test
    void anulacionTotalEmiteUnaVez() {
        Gasto g = gasto(false);
        svc.sincronizarDesdeSolicitudPago(sp(TipoSolicitudPago.GASTO, "0"));
        verify(gastoService, times(1)).emitirGastoCancelado(g);
    }

    @Test
    void yaCanceladoNoEmite() {
        gasto(true);
        svc.sincronizarDesdeSolicitudPago(sp(TipoSolicitudPago.GASTO, "0"));
        verify(gastoService, never()).emitirGastoCancelado(any());
    }

    @Test
    void pagoParcialNoEmite() {
        gasto(false);
        svc.sincronizarDesdeSolicitudPago(sp(TipoSolicitudPago.GASTO, "5000"));
        verify(gastoService, never()).emitirGastoCancelado(any());
    }

    @Test
    void otroTipoNoHaceNada() {
        svc.sincronizarDesdeSolicitudPago(sp(TipoSolicitudPago.COMPRA, "0"));
        verifyNoInteractions(repo, gastoService);
    }
}
