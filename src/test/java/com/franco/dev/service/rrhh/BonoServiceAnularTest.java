package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Anular un bono recurrente apaga su plantilla: si no, el job lo vuelve a generar
 * el mes siguiente (caso real: bodega, funcionario 448, noviembre 2026).
 */
class BonoServiceAnularTest {

    private BonoRepository repository;
    private BonoRecurrenteRepository plantillaRepository;
    private BonoService service;

    @BeforeEach
    void setUp() {
        repository = mock(BonoRepository.class);
        plantillaRepository = mock(BonoRecurrenteRepository.class);
        LiquidacionItemRepository liquidacionItemRepository = mock(LiquidacionItemRepository.class);
        service = new BonoService(repository, plantillaRepository, liquidacionItemRepository);
        when(repository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(plantillaRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> i.getArgument(0));
        when(liquidacionItemRepository.existeEnLiquidacionCerrada(anyLong())).thenReturn(false);
    }

    private Bono bono(Long plantillaId, YearMonth mes) {
        Funcionario f = new Funcionario();
        f.setId(448L);
        Bono b = new Bono();
        b.setId(207L);
        b.setFuncionario(f);
        b.setTipo(BonoTipo.OTRO);
        b.setMonto(new BigDecimal("256000"));
        b.setFecha(mes.atDay(1));
        b.setPeriodo(plantillaId != null ? mes.toString() : null);
        b.setEsRecurrente(plantillaId != null);
        b.setBonoRecurrenteId(plantillaId);
        b.setAnulado(false);
        when(repository.findById(207L)).thenReturn(Optional.of(b));
        return b;
    }

    private BonoRecurrente plantilla(boolean activa) {
        BonoRecurrente p = new BonoRecurrente();
        p.setId(43L);
        p.setActivo(activa);
        when(plantillaRepository.findById(43L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void anularUnBonoRecurrenteApagaSuPlantilla() {
        bono(43L, YearMonth.now());
        BonoRecurrente p = plantilla(true);

        Bono anulado = service.anular(207L);

        assertTrue(anulado.getAnulado());
        assertFalse(p.getActivo());
        verify(plantillaRepository).save(p);
    }

    @Test
    void anularUnBonoManualNoTocaPlantillas() {
        bono(null, YearMonth.now());

        assertTrue(service.anular(207L).getAnulado());
        verify(plantillaRepository, never()).findById(anyLong());
        verify(plantillaRepository, never()).save(any());
    }

    @Test
    void anularConLaPlantillaYaApagadaNoLaVuelveAGuardar() {
        bono(43L, YearMonth.now());
        plantilla(false);

        assertTrue(service.anular(207L).getAnulado());
        verify(plantillaRepository, never()).save(any());
    }

    @Test
    void anularUnBonoInexistenteFalla() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(GraphQLException.class, () -> service.anular(999L));
    }

    /** Decision de Franco (14/11/2026): anular siempre corta la repeticion, aunque el bono sea de un mes viejo. */
    @Test
    void anularUnBonoDeUnMesViejoTambienApagaLaPlantilla() {
        bono(43L, YearMonth.now().minusMonths(2));
        BonoRecurrente p = plantilla(true);

        service.anular(207L);

        assertFalse(p.getActivo());
    }

    @Test
    void unBonoAnuladoNoSePuedeEditarNiReactivarSuPlantilla() {
        Bono previo = bono(43L, YearMonth.now()); // lo que findById devuelve: el guardado
        previo.setAnulado(true);
        BonoRecurrente p = plantilla(false);

        assertNotNull(service.motivoNoEditable(previo));

        // Lo que manda el dialogo: el mismo bono, con el toggle encendido.
        Bono edicion = new Bono();
        edicion.setId(207L);
        edicion.setFuncionario(previo.getFuncionario());
        edicion.setFecha(previo.getFecha());
        edicion.setMonto(previo.getMonto());
        edicion.setBonoRecurrenteId(43L);
        assertThrows(GraphQLException.class,
                () -> service.saveConRecurrencia(edicion, true, BonoFrecuencia.MENSUAL));
        assertFalse(p.getActivo());
        verify(plantillaRepository, never()).save(any());
    }
}
