package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El toggle "Recurrente" del dialogo de bonos vuelve a hacer algo: saveBono crea,
 * actualiza o desactiva la plantilla. Como edita plata y una regla que se repite
 * todos los meses, cada transicion tiene su test.
 */
class BonoServiceRecurrenciaTest {

    private BonoRepository repository;
    private BonoRecurrenteRepository plantillaRepository;
    private BonoService service;

    @BeforeEach
    void setUp() {
        repository = mock(BonoRepository.class);
        plantillaRepository = mock(BonoRecurrenteRepository.class);
        service = new BonoService(repository, plantillaRepository);
        when(repository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(plantillaRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> {
            BonoRecurrente p = i.getArgument(0);
            if (p.getId() == null) p.setId(77L);
            return p;
        });
    }

    private Bono bono(Long id, Long plantillaId) {
        Funcionario f = new Funcionario();
        f.setId(7L);
        Bono b = new Bono();
        b.setId(id);
        b.setFuncionario(f);
        b.setTipo(BonoTipo.PRODUCTIVIDAD);
        b.setMonto(new BigDecimal("150000"));
        b.setFecha(LocalDate.of(2026, 9, 8));
        b.setMotivo("TRANSPORTE");
        b.setBonoRecurrenteId(plantillaId);
        return b;
    }

    @Test
    void toggleEncendidoSinPlantillaPreviaCreaLaPlantillaYEnlazaElBono() {
        Bono guardado = service.saveConRecurrencia(bono(null, null), true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        BonoRecurrente p = cap.getValue();
        assertEquals(7L, p.getFuncionario().getId());
        assertEquals(BonoTipo.PRODUCTIVIDAD, p.getTipo());
        assertEquals(new BigDecimal("150000"), p.getMonto());
        assertEquals(BonoFrecuencia.MENSUAL, p.getFrecuencia());
        assertEquals(Boolean.TRUE, p.getActivo());

        assertEquals(77L, guardado.getBonoRecurrenteId());
        assertEquals("2026-09", guardado.getPeriodo());
        assertEquals(Boolean.TRUE, guardado.getEsRecurrente());
        assertEquals(BonoFrecuencia.MENSUAL, guardado.getFrecuencia());
    }

    @Test
    void editarUnBonoRecurrenteActualizaLaPlantillaConElMontoNuevo() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setMonto(new BigDecimal("150000"));
        existente.setActivo(true);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono editado = bono(5L, 77L);
        editado.setMonto(new BigDecimal("200000"));
        service.saveConRecurrencia(editado, true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(77L, cap.getValue().getId());
        assertEquals(new BigDecimal("200000"), cap.getValue().getMonto());
        // No se crea una plantilla nueva: se reusa la enlazada.
        verify(plantillaRepository, times(1)).save(any(BonoRecurrente.class));
    }

    @Test
    void apagarElToggleDesactivaLaPlantillaPeroConservaElBono() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setActivo(true);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono guardado = service.saveConRecurrencia(bono(5L, 77L), false, null);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(Boolean.FALSE, cap.getValue().getActivo());
        assertEquals(Boolean.FALSE, guardado.getEsRecurrente());
        verify(repository).save(any(Bono.class));
    }

    @Test
    void volverAPrenderElToggleReactivaLaMismaPlantillaYNoCreaOtra() {
        BonoRecurrente existente = new BonoRecurrente();
        existente.setId(77L);
        existente.setActivo(false);
        when(plantillaRepository.findById(77L)).thenReturn(Optional.of(existente));
        when(repository.findById(5L)).thenReturn(Optional.of(bono(5L, 77L)));

        Bono guardado = service.saveConRecurrencia(bono(5L, 77L), true, BonoFrecuencia.MENSUAL);

        ArgumentCaptor<BonoRecurrente> cap = ArgumentCaptor.forClass(BonoRecurrente.class);
        verify(plantillaRepository).save(cap.capture());
        assertEquals(77L, cap.getValue().getId());
        assertEquals(Boolean.TRUE, cap.getValue().getActivo());
        assertEquals(77L, guardado.getBonoRecurrenteId());
    }

    @Test
    void unBonoManualNoCreaNiTocaPlantillas() {
        service.saveConRecurrencia(bono(null, null), false, null);

        verify(plantillaRepository, never()).save(any(BonoRecurrente.class));
        verify(repository).save(any(Bono.class));
    }

    @Test
    void noSePuedeEditarUnBonoYaLiquidado() {
        Bono previo = bono(5L, null);
        previo.setLiquidacionId(99L);
        when(repository.findById(5L)).thenReturn(Optional.of(previo));

        assertThrows(GraphQLException.class,
                () -> service.saveConRecurrencia(bono(5L, null), false, null));
        verify(repository, never()).save(any(Bono.class));
    }
}
