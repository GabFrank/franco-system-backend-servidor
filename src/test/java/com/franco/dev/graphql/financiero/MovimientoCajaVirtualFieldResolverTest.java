package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.MovimientoCajaVirtual;
import com.franco.dev.repository.financiero.PagoSolicitudDetalleRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code esPagoConsolidado} sale de la @Formula de la entidad (mismo select de la página): no se consulta una vez
 * por fila. Solo una entidad recién guardada, sin el valor, cae al exists (issue desktop #646).
 */
class MovimientoCajaVirtualFieldResolverTest {

    private final PagoSolicitudDetalleRepository repo = mock(PagoSolicitudDetalleRepository.class);
    private final MovimientoCajaVirtualFieldResolver resolver = new MovimientoCajaVirtualFieldResolver(repo);

    private static MovimientoCajaVirtual mov(Boolean formula) {
        MovimientoCajaVirtual m = new MovimientoCajaVirtual();
        m.setId(7L);
        m.setEsPagoConsolidado(formula);
        return m;
    }

    @Test
    void conElValorDeLaFormulaNoConsultaElRepositorio() {
        assertTrue(resolver.esPagoConsolidado(mov(true)));
        assertFalse(resolver.esPagoConsolidado(mov(false)));
        verify(repo, never()).existsByMovimientoCajaVirtualId(anyLong());
    }

    @Test
    void sinElValorDeLaFormulaCaeAlExists() {
        when(repo.existsByMovimientoCajaVirtualId(7L)).thenReturn(true);
        assertTrue(resolver.esPagoConsolidado(mov(null)));
        verify(repo).existsByMovimientoCajaVirtualId(7L);
    }

    @Test
    void sinIdEsFalseSinConsultar() {
        assertFalse(resolver.esPagoConsolidado(new MovimientoCajaVirtual()));
        verify(repo, never()).existsByMovimientoCajaVirtualId(anyLong());
    }
}
