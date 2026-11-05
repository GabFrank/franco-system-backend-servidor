package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.impresion.ImpresionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Si el balance no se imprimio, el que lo pidio se entera en vez de recibir la caja como si nada. */
class PdvCajaServiceImprimirBalanceTest {

    @Mock private PdvCajaRepository repository;
    @Mock private ImpresionService impresionService;
    @InjectMocks private PdvCajaService service;

    private PdvCajaService spy;
    private final PdvCaja caja = new PdvCaja();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        spy = spy(service);
        when(repository.findByIdAndSucursalId(7166L, 1L)).thenReturn(caja);
        doReturn(new PdvCajaBalanceDto()).when(spy).generarBalance(caja);
    }

    @Test
    void siNoSeImprimioAvisa() {
        when(impresionService.printBalance(any(), any(), any())).thenReturn(false);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> spy.imprimirBalance(new EmbebedPrimaryKey(7166L, 1L), "ticket", "Caja 1"));

        assertEquals("No se pudo imprimir el balance", e.getMessage());
    }

    @Test
    void siSeImprimioDevuelveLaCaja() {
        when(impresionService.printBalance(any(), any(), any())).thenReturn(true);

        assertSame(caja, spy.imprimirBalance(new EmbebedPrimaryKey(7166L, 1L), "ticket", "Caja 1"));
    }
}
