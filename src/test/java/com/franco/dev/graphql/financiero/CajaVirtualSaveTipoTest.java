package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaVirtual;
import com.franco.dev.domain.financiero.enums.CajaVirtualTipo;
import com.franco.dev.graphql.financiero.input.CajaVirtualInput;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * saveCajaVirtual con CAJA_CHICA retirado (issue #220): toda caja se guarda CAJA_MAYOR, aunque un
 * desktop viejo mande CAJA_CHICA, y limite_gs no se pisa con null.
 */
class CajaVirtualSaveTipoTest {

    private CajaVirtualService service;
    private CajaVirtualGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(CajaVirtualService.class);
        resolver = new CajaVirtualGraphQL(service, mock(SucursalService.class), mock(FuncionarioService.class),
                mock(UsuarioService.class), mock(CajaVirtualSaldoRepository.class),
                mock(CajaVirtualConfiguracionRepository.class), mock(AcreditacionPosRepository.class),
                mock(TesoreriaSecurityService.class), mock(CajaVirtualAccesoService.class),
                mock(RrhhSecurityService.class), mock(CajaVirtualSucursalService.class));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CajaVirtualInput input(Long id, CajaVirtualTipo tipo, Double limite) {
        CajaVirtualInput in = new CajaVirtualInput();
        in.setId(id);
        in.setNombre("CAJA");
        in.setTipo(tipo);
        in.setLimiteGs(limite);
        in.setActivo(true);
        return in;
    }

    @Test
    void alta_con_caja_chica_de_un_cliente_viejo_queda_caja_mayor() {
        CajaVirtual out = resolver.saveCajaVirtual(input(null, CajaVirtualTipo.CAJA_CHICA, null));
        assertEquals(CajaVirtualTipo.CAJA_MAYOR, out.getTipo());
    }

    @Test
    void alta_sin_tipo_queda_caja_mayor() {
        CajaVirtual out = resolver.saveCajaVirtual(input(null, null, null));
        assertEquals(CajaVirtualTipo.CAJA_MAYOR, out.getTipo());
    }

    @Test
    void editar_sin_limite_no_pisa_el_limite_guardado() {
        CajaVirtual existente = new CajaVirtual();
        existente.setId(7L);
        existente.setTipo(CajaVirtualTipo.CAJA_MAYOR);
        existente.setLimiteGs(10_000_000d);
        when(service.findById(7L)).thenReturn(Optional.of(existente));

        CajaVirtual out = resolver.saveCajaVirtual(input(7L, CajaVirtualTipo.CAJA_MAYOR, null));

        assertEquals(10_000_000d, out.getLimiteGs());
    }
}
