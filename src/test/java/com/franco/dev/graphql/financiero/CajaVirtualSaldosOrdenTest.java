package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtualConfiguracion;
import com.franco.dev.domain.financiero.CajaVirtualSaldo;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.dto.CajaVirtualSaldoItem;
import com.franco.dev.repository.financiero.AcreditacionPosRepository;
import com.franco.dev.repository.financiero.CajaVirtualConfiguracionRepository;
import com.franco.dev.repository.financiero.CajaVirtualSaldoRepository;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaVirtualAccesoService;
import com.franco.dev.service.financiero.CajaVirtualService;
import com.franco.dev.service.financiero.CajaVirtualSucursalService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** cajaVirtualSaldos devuelve las monedas en orden fijo (issue #540), aunque el repo las traiga mezcladas. */
class CajaVirtualSaldosOrdenTest {

    private CajaVirtualSaldoRepository saldos;
    private CajaVirtualConfiguracionRepository config;
    private CajaVirtualGraphQL resolver;

    @BeforeEach
    void setUp() {
        saldos = mock(CajaVirtualSaldoRepository.class);
        config = mock(CajaVirtualConfiguracionRepository.class);
        resolver = new CajaVirtualGraphQL(mock(CajaVirtualService.class), mock(SucursalService.class),
                mock(FuncionarioService.class), mock(UsuarioService.class), saldos, config,
                mock(AcreditacionPosRepository.class), mock(TesoreriaSecurityService.class),
                mock(CajaVirtualAccesoService.class), mock(RrhhSecurityService.class),
                mock(CajaVirtualSucursalService.class));
        // Como sale de Postgres sin ORDER BY tras actualizar el saldo de la moneda 1: al final.
        when(saldos.findByCajaVirtualId(1L)).thenReturn(Arrays.asList(saldo(3), saldo(2), saldo(1)));
    }

    private static CajaVirtualSaldo saldo(long monedaId) {
        Moneda m = new Moneda();
        m.setId(monedaId);
        CajaVirtualSaldo s = new CajaVirtualSaldo();
        s.setMoneda(m);
        s.setSaldo(BigDecimal.ONE);
        return s;
    }

    private List<Long> ids() {
        return resolver.cajaVirtualSaldos(1L).stream()
                .map(CajaVirtualSaldoItem::getMoneda).map(Moneda::getId).collect(Collectors.toList());
    }

    @Test
    void sin_configuracion_ordena_por_id_de_moneda() {
        when(config.findByCajaVirtualId(1L)).thenReturn(Optional.empty());
        assertEquals(Arrays.asList(1L, 2L, 3L), ids());
    }

    @Test
    void con_orden_configurado_lo_respeta_y_la_moneda_nueva_va_al_final() {
        CajaVirtualConfiguracion c = new CajaVirtualConfiguracion();
        c.setMonedasOrden("[2,1]");
        when(config.findByCajaVirtualId(1L)).thenReturn(Optional.of(c));
        assertEquals(Arrays.asList(2L, 1L, 3L), ids());
    }
}
