package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Un desktop viejo que escanea el QR de un retiro abre la verificación sin el detalle por moneda y
 * confirma sin conteos. Acreditar eso es acreditar 0 y abrir un caso por todo lo declarado.
 */
class RetiroVerificacionConteoVacioTest {

    @Test
    void sin_conteos_un_retiro_con_declarado_se_rechaza_sin_acreditar_ni_abrir_caso() {
        RetiroRepository retiroRepository = mock(RetiroRepository.class);
        RetiroDetalleService retiroDetalleService = mock(RetiroDetalleService.class);
        RetiroVerificacionRepository verificacionRepository = mock(RetiroVerificacionRepository.class);
        RetiroCasoRepository casoRepository = mock(RetiroCasoRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        TesoreriaService tesoreria = mock(TesoreriaService.class);
        RetiroVerificacionService service = new RetiroVerificacionService(retiroRepository, retiroDetalleService,
                verificacionRepository, casoRepository, mock(MovimientoCajaVirtualRepository.class), cajaVirtualService,
                mock(MonedaService.class), tesoreria, mock(TesoreriaSecurityService.class),
                mock(com.franco.dev.service.empresarial.SucursalService.class));

        Retiro retiro = new Retiro();
        retiro.setId(1151L);
        retiro.setSucursalId(24L);
        when(retiroRepository.lockByIdAndSucursalId(1151L, 24L)).thenReturn(Optional.of(retiro));
        when(verificacionRepository.findVigente(1151L, 24L)).thenReturn(Optional.empty());
        when(retiroRepository.findSituacion(1151L, 24L)).thenReturn(Optional.empty());
        when(cajaVirtualService.findById(3L)).thenReturn(Optional.of(new CajaVirtual()));
        Moneda gs = new Moneda();
        gs.setId(1L);
        RetiroDetalle detalle = new RetiroDetalle();
        detalle.setMoneda(gs);
        detalle.setCantidad(200000.0);
        when(retiroDetalleService.findByRetiroId(1151L, 24L)).thenReturn(Collections.singletonList(detalle));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.verificar(1151L, 24L, 3L, Collections.emptyList(), true, null, null));

        assertTrue(e.getMessage().startsWith("No llegó el conteo del retiro #1151"), e.getMessage());
        verifyNoInteractions(tesoreria, casoRepository);
        verify(verificacionRepository, never()).save(any());
    }
}
