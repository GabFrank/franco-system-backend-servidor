package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.FormaPagoRepository;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * saveCajaVirtualConfiguracion: el orden de cuentas bancarias (drag&drop) usa semántica PATCH.
 * Un save que no incluye {@code cuentasBancariasOrden} NO debe pisar el orden ya persistido.
 */
class CajaVirtualConfiguracionGraphQLTest {

    private CajaVirtualConfiguracionRepository repository;
    private CajaVirtualConfiguracionGraphQL resolver;
    private TesoreriaSecurityService seg;

    @BeforeEach
    void setUp() {
        repository = mock(CajaVirtualConfiguracionRepository.class);
        FormaPagoRepository formaPagoRepository = mock(FormaPagoRepository.class);
        CuentaBancariaRepository cuentaBancariaRepository = mock(CuentaBancariaRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        seg = mock(TesoreriaSecurityService.class);
        resolver = new CajaVirtualConfiguracionGraphQL(repository, formaPagoRepository,
                cuentaBancariaRepository, cajaVirtualService, seg);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CajaVirtualConfiguracionGraphQL.CajaVirtualConfiguracionInputWrapper input(String orden) {
        CajaVirtualConfiguracionGraphQL.CajaVirtualConfiguracionInputWrapper in =
                new CajaVirtualConfiguracionGraphQL.CajaVirtualConfiguracionInputWrapper();
        in.setCajaVirtualId(1L);
        in.setMostrarCuentasPorPagar(true);
        in.setCuentasBancariasOrden(orden);
        return in;
    }

    @Test
    void orden_null_no_pisa_el_orden_persistido() {
        CajaVirtualConfiguracion existente = new CajaVirtualConfiguracion();
        existente.setCajaVirtual(new CajaVirtual());
        existente.setCuentasBancariasOrden("[3,1,2]");
        when(repository.findByCajaVirtualId(1L)).thenReturn(Optional.of(existente));

        CajaVirtualConfiguracion out = resolver.saveCajaVirtualConfiguracion(input(null));

        assertEquals("[3,1,2]", out.getCuentasBancariasOrden(), "orden previo debe preservarse cuando el input no lo trae");
    }

    @Test
    void orden_provisto_se_persiste() {
        CajaVirtualConfiguracion existente = new CajaVirtualConfiguracion();
        existente.setCajaVirtual(new CajaVirtual());
        existente.setCuentasBancariasOrden("[3,1,2]");
        when(repository.findByCajaVirtualId(1L)).thenReturn(Optional.of(existente));

        CajaVirtualConfiguracion out = resolver.saveCajaVirtualConfiguracion(input("[2,1,3]"));

        assertEquals("[2,1,3]", out.getCuentasBancariasOrden());
    }

    @Test
    void ordenes_nuevos_son_patch_y_se_validan() {
        CajaVirtualConfiguracion existente = new CajaVirtualConfiguracion();
        existente.setCajaVirtual(new CajaVirtual());
        existente.setMonedasOrden("[2,1]");
        existente.setOperacionesFinancierasOrden("[\"CAMBIO_DIVISA\"]");
        when(repository.findByCajaVirtualId(1L)).thenReturn(Optional.of(existente));

        CajaVirtualConfiguracionGraphQL.CajaVirtualConfiguracionInputWrapper in = input(null);
        in.setSucursalesOrden("[ 5, 3 ]");
        CajaVirtualConfiguracion out = resolver.saveCajaVirtualConfiguracion(in);

        assertEquals("[2,1]", out.getMonedasOrden(), "null no pisa");
        assertEquals("[\"CAMBIO_DIVISA\"]", out.getOperacionesFinancierasOrden(), "null no pisa");
        assertEquals("[5,3]", out.getSucursalesOrden(), "se guarda normalizado");
    }

    @Test
    void operacion_inexistente_se_rechaza() {
        CajaVirtualConfiguracion existente = new CajaVirtualConfiguracion();
        existente.setCajaVirtual(new CajaVirtual());
        when(repository.findByCajaVirtualId(1L)).thenReturn(Optional.of(existente));

        CajaVirtualConfiguracionGraphQL.CajaVirtualConfiguracionInputWrapper in = input(null);
        in.setOperacionesFinancierasOcultas("[\"NO_EXISTE\"]");
        assertThrows(graphql.GraphQLException.class, () -> resolver.saveCajaVirtualConfiguracion(in));
    }

    @Test
    void ordenes_se_leen_con_el_rol_de_caja_y_sin_config_vienen_null() {
        when(repository.findByCajaVirtualId(1L)).thenReturn(Optional.empty());
        com.franco.dev.graphql.financiero.dto.CajaVirtualOrdenes out = resolver.cajaVirtualOrdenes(1L);
        assertNull(out.getMonedasOrden());
        verify(seg).requireVerCaja();
        verify(seg).requireLecturaCaja(1L);
        verify(seg, never()).requireVer();
    }

    @Test
    void configurar_una_caja_exige_ser_su_duenio() {
        doThrow(new graphql.GraphQLException("No autorizado")).when(seg).requirePropietarioCaja(1L);
        assertThrows(graphql.GraphQLException.class, () -> resolver.saveCajaVirtualConfiguracion(input("[1]")));
        verify(repository, never()).save(any());
    }
}
