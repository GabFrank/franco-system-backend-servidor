package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Issue #306: desde una solicitud de compra (sin rol) se llega al pago y a sus solicitudes. Un pago anterior a #302
 * pudo mezclar compras con obligaciones de RRHH: esas solo se muestran con rol de tesoreria o de RRHH.
 */
class PagoResolverTest {

    @Mock private SolicitudPagoService solicitudPagoService;
    @Mock private TesoreriaSecurityService tesoreriaSecurityService;
    @Mock private RrhhSecurityService rrhhSecurityService;

    @InjectMocks private PagoResolver resolver;

    private AutoCloseable mocks;
    private Pago pago;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        pago = new Pago();
        pago.setId(9L);
        when(solicitudPagoService.findByPagoId(9L)).thenReturn(Arrays.asList(
                solicitud(1L, TipoSolicitudPago.COMPRA), solicitud(3L, TipoSolicitudPago.RRHH)));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static SolicitudPago solicitud(long id, TipoSolicitudPago tipo) {
        SolicitudPago s = new SolicitudPago();
        s.setId(id);
        s.setTipo(tipo);
        return s;
    }

    private static List<Long> ids(List<SolicitudPago> l) {
        return l.stream().map(SolicitudPago::getId).collect(Collectors.toList());
    }

    @Test
    void sin_rol_omite_las_obligaciones_rrhh() {
        assertEquals(Arrays.asList(1L), ids(resolver.solicitudesPago(pago)));
    }

    @Test
    void con_rol_de_rrhh_las_muestra() {
        when(rrhhSecurityService.hasAnyRole(RrhhSecurityService.TODOS)).thenReturn(true);

        assertEquals(Arrays.asList(1L, 3L), ids(resolver.solicitudesPago(pago)));
    }

    @Test
    void con_rol_de_tesoreria_las_muestra() {
        when(tesoreriaSecurityService.hasAnyRole(TesoreriaSecurityService.TODOS)).thenReturn(true);

        assertEquals(Arrays.asList(1L, 3L), ids(resolver.solicitudesPago(pago)));
    }

    @Test
    void un_pago_sin_rrhh_no_consulta_roles() {
        when(solicitudPagoService.findByPagoId(9L)).thenReturn(Arrays.asList(solicitud(1L, TipoSolicitudPago.COMPRA)));

        assertEquals(Arrays.asList(1L), ids(resolver.solicitudesPago(pago)));
        verifyNoInteractions(tesoreriaSecurityService, rrhhSecurityService);
    }
}
