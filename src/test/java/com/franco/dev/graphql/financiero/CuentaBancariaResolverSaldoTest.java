package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.graphql.financiero.resolver.CuentaBancariaResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El rol acotado (TESORERIA CAJA) deposita a las cuentas de su caja sin ver su saldo. El control está en
 * el campo, así que vale por cualquier camino por el que llegue una cuenta.
 */
class CuentaBancariaResolverSaldoTest {

    private final RolCajaSoporte s = new RolCajaSoporte();
    private final CuentaBancariaResolver resolver = new CuentaBancariaResolver();
    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "seg", s.seg);
        cuenta = new CuentaBancaria();
        cuenta.setSaldo(new BigDecimal("119372686"));
        cuenta.setSaldoReservado(new BigDecimal("2500000"));
    }

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elRolAcotado_noVeNiElSaldoNiElReservado() {
        s.autenticar("gerente", "TESORERIA CAJA");

        assertNull(resolver.saldo(cuenta));
        assertNull(resolver.saldoReservado(cuenta));
    }

    @Test
    void losRolesDeSiempre_venElSaldo() {
        for (String rol : new String[]{"TESORERIA VER", "TESORERIA GESTIONAR", "ADMIN", "LIQUIDAR SUELDOS"}) {
            s.autenticar("usuario", rol);
            assertEquals(new BigDecimal("119372686"), resolver.saldo(cuenta), rol);
            assertEquals(new BigDecimal("2500000"), resolver.saldoReservado(cuenta), rol);
        }
    }

    @Test
    void acotadoConUnRolAmplio_veElSaldo() {
        s.autenticar("tesorero", "TESORERIA CAJA", "TESORERIA VER");

        assertEquals(new BigDecimal("119372686"), resolver.saldo(cuenta));
    }

    @Test
    void sinUsuario_noSeOculta() {
        // Un proceso de sistema (reporte programado) no es un operador acotado.
        assertEquals(new BigDecimal("119372686"), resolver.saldo(cuenta));
    }

    @Test
    void dentroDeUnRequest_elRolSeConsultaUnaSolaVez() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        s.autenticar("gerente", "TESORERIA CAJA");

        for (int i = 0; i < 10; i++) {
            assertNull(resolver.saldo(cuenta));
            assertNull(resolver.saldoReservado(cuenta));
        }

        verify(s.roleService, times(1)).findByUsuarioId(RolCajaSoporte.USUARIO_ID);
    }
}
