package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoFrecuencia;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * El generador de bonos recurrentes crea plata: un duplicado se paga dos veces y
 * un bono de un funcionario egresado se paga sin que nadie lo pida. Los casos de
 * abajo son justamente esos.
 */
class BonoRecurrenteServiceTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);

    private BonoRecurrenteRepository plantillaRepository;
    private BonoRepository bonoRepository;
    private BonoRecurrenteService service;

    @BeforeEach
    void setUp() {
        plantillaRepository = mock(BonoRecurrenteRepository.class);
        bonoRepository = mock(BonoRepository.class);
        service = new BonoRecurrenteService(plantillaRepository, bonoRepository);
        when(bonoRepository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(anyLong(), anyString())).thenReturn(false);
    }

    private Funcionario funcionario(boolean activo, LocalDateTime fechaEgreso) {
        Funcionario f = new Funcionario();
        f.setId(7L);
        f.setActivo(activo);
        f.setFechaEgreso(fechaEgreso);
        return f;
    }

    private BonoRecurrente plantilla(boolean activo, BonoFrecuencia frecuencia, Funcionario f) {
        BonoRecurrente p = new BonoRecurrente();
        p.setId(1L);
        p.setFuncionario(f);
        p.setTipo(BonoTipo.PRODUCTIVIDAD);
        p.setMonto(new BigDecimal("150000"));
        p.setMotivo("TRANSPORTE");
        p.setFrecuencia(frecuencia);
        p.setActivo(activo);
        when(plantillaRepository.findById(1L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void generaElBonoDelPeriodoConFechaDelDiaUnoYMarcaLaTrazabilidad() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));

        Optional<Bono> res = service.generarUno(1L, MARZO);

        assertTrue(res.isPresent());
        Bono b = res.get();
        assertEquals(LocalDate.of(2026, 3, 1), b.getFecha());
        assertEquals("2026-03", b.getPeriodo());
        assertEquals(1L, b.getBonoRecurrenteId());
        assertEquals(7L, b.getFuncionario().getId());
        assertEquals(new BigDecimal("150000"), b.getMonto());
        assertEquals(BonoTipo.PRODUCTIVIDAD, b.getTipo());
        assertEquals("TRANSPORTE", b.getMotivo());
        assertEquals(Boolean.TRUE, b.getEsRecurrente());
        assertEquals(BonoFrecuencia.MENSUAL, b.getFrecuencia());
        assertEquals(Boolean.FALSE, b.getAnulado());
        assertNull(b.getLiquidacionId());
        verify(bonoRepository).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElBonoDelPeriodoYaExiste() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(1L, "2026-03")).thenReturn(true);

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void dosLlamadasSeguidasGeneranUnSoloBono() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));
        // Primera llamada genera; a partir de ahi el bono existe.
        when(bonoRepository.existsByBonoRecurrenteIdAndPeriodo(1L, "2026-03"))
                .thenReturn(false).thenReturn(true);

        assertTrue(service.generarUno(1L, MARZO).isPresent());
        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, times(1)).save(any(Bono.class));
    }

    @Test
    void noGeneraSiLaPlantillaEstaInactiva() {
        plantilla(false, BonoFrecuencia.MENSUAL, funcionario(true, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElFuncionarioEstaInactivo() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(false, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiElFuncionarioTieneEgresoAnteriorAlPeriodo() {
        // Defensivo: activo quedo en true pero el egreso ya esta cargado.
        plantilla(true, BonoFrecuencia.MENSUAL,
                funcionario(true, LocalDateTime.of(2026, 1, 15, 0, 0)));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraFrecuenciasDistintasDeMensual() {
        plantilla(true, BonoFrecuencia.ANUAL, funcionario(true, null));

        assertTrue(service.generarUno(1L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void noGeneraSiLaPlantillaNoExiste() {
        when(plantillaRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(service.generarUno(99L, MARZO).isEmpty());
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void diciembreNoSeCorreDeMes() {
        plantilla(true, BonoFrecuencia.MENSUAL, funcionario(true, null));

        Bono b = service.generarUno(1L, YearMonth.of(2026, 12)).orElseThrow();

        assertEquals("2026-12", b.getPeriodo());
        assertEquals(LocalDate.of(2026, 12, 1), b.getFecha());
    }
}
