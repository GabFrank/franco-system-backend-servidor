package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.domain.financiero.RetiroVerificacion;
import com.franco.dev.domain.financiero.RetiroVerificacionDetalle;
import com.franco.dev.domain.financiero.enums.CategoriaDiferenciaRetiro;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El motivo FALTANTE/SOBRANTE sigue al signo de la diferencia, aunque el cliente mande el contrario.
 * Retiro 2108 de la 22: 200 R$ declarados, 500 contados, guardado como FALTANTE porque el desktop fijaba
 * el motivo con el primer dígito tipeado (5 < 200) y no lo recalculaba.
 */
class RetiroVerificacionCategoriaTest {

    @Test
    void faltante_con_diferencia_positiva_se_guarda_sobrante() {
        assertEquals(CategoriaDiferenciaRetiro.SOBRANTE, categoriaGuardada(200, 500, CategoriaDiferenciaRetiro.FALTANTE));
    }

    @Test
    void sobrante_con_diferencia_negativa_se_guarda_faltante() {
        assertEquals(CategoriaDiferenciaRetiro.FALTANTE, categoriaGuardada(500, 300, CategoriaDiferenciaRetiro.SOBRANTE));
    }

    @Test
    void sin_motivo_se_deriva_del_signo() {
        assertEquals(CategoriaDiferenciaRetiro.SOBRANTE, categoriaGuardada(200, 500, null));
        assertEquals(CategoriaDiferenciaRetiro.FALTANTE, categoriaGuardada(500, 200, null));
    }

    @Test
    void un_motivo_elegido_a_mano_se_respeta() {
        assertEquals(CategoriaDiferenciaRetiro.BILLETE_NO_RECIBIBLE,
                categoriaGuardada(500, 400, CategoriaDiferenciaRetiro.BILLETE_NO_RECIBIBLE));
    }

    private CategoriaDiferenciaRetiro categoriaGuardada(double declarado, double contado, CategoriaDiferenciaRetiro enviada) {
        RetiroRepository retiroRepository = mock(RetiroRepository.class);
        RetiroDetalleService retiroDetalleService = mock(RetiroDetalleService.class);
        RetiroVerificacionRepository verificacionRepository = mock(RetiroVerificacionRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        MonedaService monedaService = mock(MonedaService.class);
        RetiroVerificacionService service = new RetiroVerificacionService(retiroRepository, retiroDetalleService,
                verificacionRepository, mock(RetiroCasoRepository.class), mock(MovimientoCajaVirtualRepository.class),
                cajaVirtualService, monedaService, mock(TesoreriaService.class), mock(TesoreriaSecurityService.class),
                mock(com.franco.dev.service.empresarial.SucursalService.class));

        Retiro retiro = new Retiro();
        retiro.setId(2108L);
        retiro.setSucursalId(22L);
        when(retiroRepository.lockByIdAndSucursalId(2108L, 22L)).thenReturn(Optional.of(retiro));
        when(verificacionRepository.findVigente(2108L, 22L)).thenReturn(Optional.empty());
        when(retiroRepository.findSituacion(2108L, 22L)).thenReturn(Optional.empty());
        when(cajaVirtualService.findById(3L)).thenReturn(Optional.of(new CajaVirtual()));
        when(verificacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Moneda real = new Moneda();
        real.setId(2L);
        real.setDenominacion("REAL");
        real.setDecimales(2);
        when(monedaService.findById(2L)).thenReturn(Optional.of(real));
        RetiroDetalle detalle = new RetiroDetalle();
        detalle.setMoneda(real);
        detalle.setCantidad(declarado);
        when(retiroDetalleService.findByRetiroId(2108L, 22L)).thenReturn(Collections.singletonList(detalle));

        RetiroVerificacionService.ConteoMoneda conteo = new RetiroVerificacionService.ConteoMoneda();
        conteo.setMonedaId(2L);
        conteo.setContado(BigDecimal.valueOf(contado));
        conteo.setCategoria(enviada);

        // acreditar() y abrirCaso() tocan mocks: lo que importa es lo que se guardó.
        try {
            service.verificar(2108L, 22L, 3L, Collections.singletonList(conteo), true, null, null);
        } catch (RuntimeException ignorada) {
            // pasos posteriores al save sobre mocks vacíos; el detalle ya quedó capturado
        }

        ArgumentCaptor<RetiroVerificacion> guardada = ArgumentCaptor.forClass(RetiroVerificacion.class);
        verify(verificacionRepository).save(guardada.capture());
        RetiroVerificacionDetalle d = guardada.getValue().getDetalles().get(0);
        return d.getCategoria();
    }
}
