package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.FuncionarioEgresoHistorico;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.personas.FuncionarioRepository;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.repository.rrhh.FuncionarioEgresoHistoricoRepository;
import com.franco.dev.repository.rrhh.LiquidacionFinalRepository;
import com.franco.dev.service.empresarial.CargoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Al egresar, lo que el funcionario no alcanzo a cobrar deja de corresponderle: los bonos
 * pendientes se anulan y la plantilla que los genera todos los meses se apaga.
 *
 * <p>Decision de negocio (issue #276): el finiquito NO paga bonos. Sin esto el bono del mes
 * queda con liquidacion_id nulo para siempre -- nadie lo paga, pero figura como pendiente
 * en la grilla, y la plantilla sigue en "activa" aunque el generador ya la saltee por
 * funcionario.activo.</p>
 */
class AnularBonosAlEgresarTest {

    private static final Long FUNC_ID = 1L;
    private static final Long PERSONA_ID = 7L;

    private FuncionarioRepository funcionarioRepository;
    private BonoRepository bonoRepository;
    private BonoRecurrenteRepository bonoRecurrenteRepository;
    private FuncionarioRrhhService service;

    private Funcionario funcionario;

    @BeforeEach
    void setUp() {
        funcionarioRepository = mock(FuncionarioRepository.class);
        bonoRepository = mock(BonoRepository.class);
        bonoRecurrenteRepository = mock(BonoRecurrenteRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        ClienteService clienteService = mock(ClienteService.class);
        FuncionarioEgresoHistoricoRepository egresoHistoricoRepository =
                mock(FuncionarioEgresoHistoricoRepository.class);

        FuncionarioService funcionarioService =
                new FuncionarioService(funcionarioRepository, usuarioService, clienteService);

        service = new FuncionarioRrhhService(
                funcionarioService,
                mock(CargoService.class),
                mock(MonedaService.class),
                usuarioService,
                mock(FuncionarioCargoHistoricoService.class),
                mock(FuncionarioSalarioHistoricoService.class),
                clienteService,
                mock(LiquidacionFinalRepository.class),
                egresoHistoricoRepository,
                bonoRepository,
                bonoRecurrenteRepository);

        Persona persona = new Persona();
        persona.setId(PERSONA_ID);

        funcionario = new Funcionario();
        funcionario.setId(FUNC_ID);
        funcionario.setPersona(persona);
        funcionario.setActivo(true);
        funcionario.setCredito(0f);

        when(funcionarioRepository.findById(FUNC_ID)).thenReturn(Optional.of(funcionario));
        when(funcionarioRepository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));
        when(egresoHistoricoRepository.save(any(FuncionarioEgresoHistorico.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(bonoRepository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRecurrenteRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> i.getArgument(0));
        when(bonoRepository.findByFuncionarioIdOrderByFechaDesc(FUNC_ID))
                .thenReturn(Collections.emptyList());
        when(bonoRecurrenteRepository.findByFuncionarioIdAndActivoTrue(FUNC_ID))
                .thenReturn(Collections.emptyList());
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
    void anulaElBonoPendienteDelFuncionarioQueEgresa() {
        Bono pendiente = bono(10L, false, null);
        when(bonoRepository.findByFuncionarioIdOrderByFechaDesc(FUNC_ID))
                .thenReturn(new ArrayList<>(Collections.singletonList(pendiente)));

        service.egresar(FUNC_ID, LocalDate.of(2026, 9, 12), "renuncia");

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

        service.egresar(FUNC_ID, LocalDate.of(2026, 9, 12), "renuncia");

        assertFalse(Boolean.TRUE.equals(liquidado.getAnulado()),
                "un bono ya liquidado es plata pagada: no se anula");
        verify(bonoRepository, never()).save(any(Bono.class));
    }

    @Test
    void apagaLaPlantillaRecurrenteDelFuncionarioQueEgresa() {
        BonoRecurrente p = plantilla(77L);
        when(bonoRecurrenteRepository.findByFuncionarioIdAndActivoTrue(FUNC_ID))
                .thenReturn(new ArrayList<>(Collections.singletonList(p)));

        service.egresar(FUNC_ID, LocalDate.of(2026, 9, 12), "renuncia");

        assertFalse(Boolean.TRUE.equals(p.getActivo()),
                "la plantilla recurrente tiene que quedar inactiva");
        verify(bonoRecurrenteRepository).save(p);
    }

    @Test
    void elEgresoSigueFuncionandoSinBonosNiPlantillas() {
        Funcionario r = service.egresar(FUNC_ID, LocalDate.of(2026, 9, 12), "renuncia");

        assertFalse(Boolean.TRUE.equals(r.getActivo()), "el funcionario tiene que quedar inactivo");
        assertNotNull(r.getFechaEgreso(), "la fecha de egreso tiene que quedar cargada");
    }
}
