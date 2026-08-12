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

    @BeforeEach
    void setUp() {
        repository = mock(CajaVirtualConfiguracionRepository.class);
        FormaPagoRepository formaPagoRepository = mock(FormaPagoRepository.class);
        CuentaBancariaRepository cuentaBancariaRepository = mock(CuentaBancariaRepository.class);
        CajaVirtualService cajaVirtualService = mock(CajaVirtualService.class);
        TesoreriaSecurityService seg = mock(TesoreriaSecurityService.class);
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
}
