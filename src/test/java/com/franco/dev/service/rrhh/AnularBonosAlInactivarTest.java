package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.service.personas.event.FuncionarioInactivadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lo que el funcionario no alcanzo a cobrar deja de corresponderle cuando lo dan de baja:
 * los bonos pendientes se anulan y la plantilla que los repite se apaga.
 *
 * <p>Decision de negocio (issue #276): el finiquito NO paga bonos. Cuelga de la transicion
 * activo -> inactivo y no del boton "Egresar" porque el camino real de RRHH es el pago del
 * finiquito, que nunca pasa por {@code egresar()} (issue #295).</p>
 */
class AnularBonosAlInactivarTest {

    private static final Long FUNC_ID = 9L;

    private BonoRepository bonoRepository;
    private BonoRecurrenteRepository bonoRecurrenteRepository;
    private BonosAlInactivarFuncionarioListener listener;
    private Funcionario funcionario;

    @BeforeEach
    void setUp() {
        bonoRepository = mock(BonoRepository.class);
        bonoRecurrenteRepository = mock(BonoRecurrenteRepository.class);
        listener = new BonosAlInactivarFuncionarioListener(bonoRepository, bonoRecurrenteRepository);

        funcionario = new Funcionario();
        funcionario.setId(FUNC_ID);

        when(bonoRepository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRecurrenteRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRepository.findByFuncionarioIdOrderByFechaDesc(FUNC_ID)).thenReturn(Collections.emptyList());
        when(bonoRecurrenteRepository.findByFuncionarioIdAndActivoTrue(FUNC_ID)).thenReturn(Collections.emptyList());
    }

    private void inactivar() {
        listener.onFuncionarioInactivado(new FuncionarioInactivadoEvent(this, FUNC_ID));
    }

    private Bono bono(Long id, Boolean anulado, Long liquidacionId) {
        Bono b = new Bono();
        b.setId(id);
        b.setFuncionario(funcionario);
        b.setTipo(BonoTipo.PRODUCTIVIDAD);
        b.setMonto(new BigDecimal("150000"));
        b.setFecha(LocalDate.of(2026, 9, 1));
        b.setAnulado(anulado);
        b.setLiquidacionId(liquidacionId);
        return b;
    }

    private BonoRecurrente plantilla(Long id) {
        BonoRecurrente p = new BonoRecurrente();
        p.setId(id);
        p.setFuncionario(funcionario);
        p.setTipo(BonoTipo.PRODUCTIVIDAD);
        p.setMonto(new BigDecimal("150000"));
        p.setActivo(true);
        return p;
    }

    @Test
    void anulaElBonoPendienteDelFuncionarioDadoDeBaja() {
        Bono pendiente = bono(10L, false, null);
        when(bonoRepository.findByFuncionarioIdOrderByFechaDesc(FUNC_ID))
                .thenReturn(new ArrayList<>(Collections.singletonList(pendiente)));

        inactivar();

        assertTrue(Boolean.TRUE.equals(pendiente.getAnulado()),
                "el bono sin liquidar tiene que quedar anulado");
        verify(bonoRepository).save(pendiente);
    }

    @Test
    void noTocaElBonoYaLiquidadoNiElYaAnulado() {
        Bono liquidado = bono(11L, false, 500L);
        Bono yaAnulado = bono(12L, true, null);
        when(bonoRepository.findByFuncionarioIdOrderByFechaDesc(FUNC_ID))
                .thenReturn(new ArrayList<>(Arrays.asList(liquidado, yaAnulado)));

        inactivar();

        assertFalse(Boolean.TRUE.equals(liquidado.getAnulado()),
                "un bono ya liquidado es plata pagada: no se anula");
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void apagaLaPlantillaRecurrenteDelFuncionarioDadoDeBaja() {
        BonoRecurrente p = plantilla(77L);
        when(bonoRecurrenteRepository.findByFuncionarioIdAndActivoTrue(FUNC_ID))
                .thenReturn(new ArrayList<>(Collections.singletonList(p)));

        inactivar();

        assertFalse(Boolean.TRUE.equals(p.getActivo()),
                "la plantilla recurrente tiene que quedar inactiva");
        verify(bonoRecurrenteRepository).save(p);
    }

    @Test
    void noHaceNadaSiElFuncionarioNoTieneBonosNiPlantillas() {
        inactivar();

        verify(bonoRepository, never()).save(any(Bono.class));
        verify(bonoRecurrenteRepository, never()).save(any(BonoRecurrente.class));
    }
}
