package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GastoServiceTest {

    private GastoRepository repository;
    private ApplicationEventPublisher publisher;
    private GastoService service;

    @BeforeEach
    void setUp() {
        repository = mock(GastoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new GastoService(repository, publisher);
    }

    @Test
    void cancelarGasto_gastoNuncaCanceladoQuedaCancelado() {
        Gasto gasto = new Gasto();
        gasto.setCancelado(null);

        Boolean resultado = service.cancelarGasto(gasto);

        assertTrue(resultado);
        assertEquals(Boolean.TRUE, gasto.getCancelado());
        verify(repository).save(gasto);
    }

    @Test
    void cancelarGasto_gastoCanceladoSeRehabilita() {
        Gasto gasto = new Gasto();
        gasto.setCancelado(true);

        service.cancelarGasto(gasto);

        assertEquals(Boolean.FALSE, gasto.getCancelado());
        verify(repository).save(gasto);
    }

    @Test
    void cancelarGasto_noPublicaGastoRealizadoEvent() {
        Gasto gasto = new Gasto();

        service.cancelarGasto(gasto);

        // Cancelar no es realizar un gasto: no tiene que disparar la push
        // notification. Por eso el service persiste con repository.save() y no
        // con this.save(), cuyo override publica GastoRealizadoEvent.
        verify(publisher, never()).publishEvent(any());
    }
}
