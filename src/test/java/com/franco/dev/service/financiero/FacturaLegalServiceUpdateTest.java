package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.repository.financiero.FacturaLegalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una electrónica no se edita: su receptor cambia solo cuando SIFEN aprueba la nominación. Antes el
 * update guardaba el cliente de una innominada y la nominación posterior la veía «ya nominada» (#433).
 */
class FacturaLegalServiceUpdateTest {

    @Mock private FacturaLegalRepository repository;
    @InjectMocks private FacturaLegalService service;

    private AutoCloseable mocks;
    private FacturaLegal existente;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        existente = new FacturaLegal();
        existente.setId(300L);
        existente.setSucursalId(1L);
        existente.setNombre("SIN NOMBRE");
        when(repository.findByIdAndSucursalId(300L, 1L)).thenReturn(existente);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private FacturaLegal cambio() {
        FacturaLegal cambio = new FacturaLegal();
        cambio.setId(300L);
        cambio.setSucursalId(1L);
        cambio.setNombre("MAURO RIVAS");
        cambio.setCliente(new Cliente());
        return cambio;
    }

    @Test
    void electronicaInnominadaNoSeEdita() {
        existente.setCdc("01800000000000000000000000000000000000000000");

        assertThrows(IllegalStateException.class, () -> service.update(cambio()));
        verify(repository, never()).save(any());
    }

    @Test
    void electronicaConClienteTampoco() {
        existente.setCdc("01800000000000000000000000000000000000000000");
        existente.setCliente(new Cliente());

        assertThrows(IllegalStateException.class, () -> service.update(cambio()));
        verify(repository, never()).save(any());
    }

    @Test
    void enPapelSeEditaComoAntes() {
        FacturaLegal guardada = service.update(cambio());

        assertEquals("MAURO RIVAS", guardada.getNombre());
        verify(repository).save(existente);
    }
}
