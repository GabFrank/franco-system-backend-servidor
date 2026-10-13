package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.repository.financiero.MovimientoCajaVirtualRepository;
import com.franco.dev.repository.financiero.RetiroCasoRepository;
import com.franco.dev.repository.financiero.RetiroRepository;
import com.franco.dev.repository.financiero.RetiroVerificacionRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verificar un retiro (contarlo y acreditarlo) es su único ingreso desde la pantalla, así que lo hace
 * también el rol acotado TESORERIA CAJA: en una caja que puede mover y solo con retiros de la sucursal
 * de esa caja.
 */
class RetiroVerificacionRolCajaTest {

    private static final Long CAJA = 19L;

    private RetiroRepository retiroRepository;
    private CajaVirtualService cajaVirtualService;
    private TesoreriaSecurityService seguridad;
    private RetiroVerificacionService service;
    private CajaVirtual caja;

    @BeforeEach
    void setUp() {
        retiroRepository = mock(RetiroRepository.class);
        cajaVirtualService = mock(CajaVirtualService.class);
        seguridad = mock(TesoreriaSecurityService.class);
        service = new RetiroVerificacionService(retiroRepository, mock(RetiroDetalleService.class),
                mock(RetiroVerificacionRepository.class), mock(RetiroCasoRepository.class),
                mock(MovimientoCajaVirtualRepository.class), cajaVirtualService, mock(MonedaService.class),
                mock(TesoreriaService.class), seguridad, mock(com.franco.dev.service.empresarial.SucursalService.class));

        caja = new CajaVirtual();
        caja.setId(CAJA);
        when(cajaVirtualService.findById(CAJA)).thenReturn(Optional.of(caja));
        when(seguridad.operaAcotado()).thenReturn(true);
        // El retiro no existe: alcanza para saber hasta dónde llegó el servicio.
        when(retiroRepository.lockByIdAndSucursalId(anyLong(), anyLong())).thenReturn(Optional.empty());
    }

    private void cajaDeLaSucursal(Long sucursalId) {
        Sucursal s = new Sucursal();
        s.setId(sucursalId);
        caja.setSucursal(s);
    }

    private GraphQLException verificar(Long sucursalDelRetiro) {
        return assertThrows(GraphQLException.class, () -> service.verificar(
                700L, sucursalDelRetiro, CAJA, Collections.emptyList(), true, null, null));
    }

    @Test
    void el_rol_acotado_verifica_un_retiro_de_la_sucursal_de_su_caja() {
        cajaDeLaSucursal(12L);

        GraphQLException e = verificar(12L);

        // Pasó el control y llegó a buscar el retiro.
        assertTrue(e.getMessage().startsWith("Retiro no encontrado"), e.getMessage());
        verify(seguridad).requireEscrituraCaja(CAJA);
        verify(retiroRepository).lockByIdAndSucursalId(700L, 12L);
    }

    @Test
    void el_rol_acotado_no_verifica_un_retiro_de_otra_sucursal_ni_averigua_si_existe() {
        cajaDeLaSucursal(12L);

        GraphQLException e = verificar(1L);

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        verifyNoInteractions(retiroRepository);
    }

    @Test
    void el_rol_acotado_no_verifica_en_una_caja_sin_sucursal() {
        GraphQLException e = verificar(12L);

        assertTrue(e.getMessage().startsWith("No autorizado"), e.getMessage());
        verifyNoInteractions(retiroRepository);
    }

    @Test
    void sin_permiso_sobre_la_caja_no_llega_a_mirar_nada() {
        cajaDeLaSucursal(12L);
        doThrow(new GraphQLException("No tenes permiso para mover plata en esta caja.")).when(seguridad).requireEscrituraCaja(CAJA);

        verificar(12L);

        verifyNoInteractions(retiroRepository);
    }

    @Test
    void quien_gestiona_verifica_en_cualquier_caja_un_retiro_de_cualquier_sucursal() {
        when(seguridad.operaAcotado()).thenReturn(false);
        cajaDeLaSucursal(12L);

        GraphQLException e = verificar(1L);

        assertTrue(e.getMessage().startsWith("Retiro no encontrado"), e.getMessage());
    }
}
