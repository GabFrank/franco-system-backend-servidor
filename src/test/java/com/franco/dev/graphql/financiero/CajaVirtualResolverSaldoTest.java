package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.graphql.financiero.resolver.CajaVirtualResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El rol acotado (TESORERIA CAJA) ve el saldo de sus cajas y no el de una ajena, que le llega anidada
 * como contraparte de una transferencia.
 */
class CajaVirtualResolverSaldoTest {

    private static final Long MIA = 15L;
    private static final Long AJENA = 16L;

    private final RolCajaSoporte s = new RolCajaSoporte();
    private final CajaVirtualResolver resolver = new CajaVirtualResolver();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "seg", s.seg);
        when(s.accesoRepository.cajasVisiblesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Collections.singletonList(MIA));
    }

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
        RequestContextHolder.resetRequestAttributes();
    }

    private CajaVirtual caja(Long id) {
        CajaVirtual c = new CajaVirtual();
        c.setId(id);
        c.setSaldoGs(8293999d);
        c.setSaldoRs(120d);
        c.setSaldoDs(50d);
        c.setLimiteGs(10000000d);
        return c;
    }

    @Test
    void elRolAcotado_veElSaldoDeSuCaja() {
        s.autenticar("gerente", "TESORERIA CAJA");

        assertEquals(8293999d, resolver.saldoGs(caja(MIA)));
        assertEquals(120d, resolver.saldoRs(caja(MIA)));
        assertEquals(50d, resolver.saldoDs(caja(MIA)));
        assertEquals(10000000d, resolver.limiteGs(caja(MIA)));
    }

    @Test
    void elRolAcotado_noVeNingunSaldoDeUnaCajaAjena() {
        s.autenticar("gerente", "TESORERIA CAJA");

        assertNull(resolver.saldoGs(caja(AJENA)));
        assertNull(resolver.saldoRs(caja(AJENA)));
        assertNull(resolver.saldoDs(caja(AJENA)));
        assertNull(resolver.limiteGs(caja(AJENA)));
    }

    @Test
    void losRolesDeSiempre_venElSaldoDeCualquierCajaQueLesLlegue() {
        // Para ellos decide la consulta, como hasta ahora: el resolver no cambia nada.
        for (String rol : new String[]{"TESORERIA VER", "TESORERIA GESTIONAR", "ADMIN", "LIQUIDAR SUELDOS"}) {
            s.autenticar("usuario", rol);
            assertEquals(8293999d, resolver.saldoGs(caja(AJENA)), rol);
        }
    }

    @Test
    void sinUsuario_noSeOculta() {
        assertEquals(8293999d, resolver.saldoGs(caja(AJENA)));
    }

    @Test
    void dentroDeUnRequest_lasCajasVisiblesSeConsultanUnaSolaVez() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        s.autenticar("gerente", "TESORERIA CAJA");

        for (int i = 0; i < 10; i++) {
            assertNull(resolver.saldoGs(caja(AJENA)));
            assertNotNull(resolver.saldoGs(caja(MIA)));
        }

        verify(s.accesoRepository, times(1)).cajasVisiblesIds(RolCajaSoporte.USUARIO_ID);
    }
}
