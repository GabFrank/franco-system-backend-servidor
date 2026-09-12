package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.rrhh.BonoRecurrenteRepository;
import com.franco.dev.repository.rrhh.BonoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Un bono para alguien que ya se fue no lo cobra nadie: el finiquito no paga bonos y la
 * generacion masiva saltea a los inactivos, asi que quedaria pendiente para siempre
 * (issue #296).
 *
 * <p>La barrera es solo para el alta. Editar o anular un bono viejo de alguien que egreso
 * despues de cobrarlo tiene que seguir siendo posible: es la unica forma de corregir uno
 * mal cargado.</p>
 */
class BonoFuncionarioInactivoTest {

    private BonoRepository repository;
    private BonoService service;

    @BeforeEach
    void setUp() {
        repository = mock(BonoRepository.class);
        BonoRecurrenteRepository plantillaRepository = mock(BonoRecurrenteRepository.class);
        LiquidacionItemRepository liquidacionItemRepository = mock(LiquidacionItemRepository.class);
        service = new BonoService(repository, plantillaRepository, liquidacionItemRepository);

        when(repository.save(any(Bono.class))).thenAnswer(i -> i.getArgument(0));
        when(plantillaRepository.save(any(BonoRecurrente.class))).thenAnswer(i -> i.getArgument(0));
        when(liquidacionItemRepository.existeEnLiquidacionCerrada(anyLong())).thenReturn(false);
    }

    private Bono bonoDe(Long bonoId, Boolean funcionarioActivo) {
        Funcionario f = new Funcionario();
        f.setId(9L);
        f.setActivo(funcionarioActivo);
        Bono b = new Bono();
        b.setId(bonoId);
        b.setFuncionario(f);
        b.setTipo(BonoTipo.PRODUCTIVIDAD);
        b.setMonto(new BigDecimal("100000"));
        b.setFecha(LocalDate.of(2026, 9, 12));
        if (bonoId != null) when(repository.findById(bonoId)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void rechazaCrearUnBonoParaUnFuncionarioInactivo() {
        Bono nuevo = bonoDe(null, false);

        GraphQLException ex = assertThrows(GraphQLException.class,
                () -> service.saveConRecurrencia(nuevo, false, null));

        assertTrue(ex.getMessage().toLowerCase().contains("inactivo")
                        || ex.getMessage().toLowerCase().contains("egres"),
                "el mensaje tiene que decir por que no se puede: " + ex.getMessage());
        verify(repository, never()).save(any(Bono.class));
    }

    @Test
    void permiteCrearUnBonoParaUnFuncionarioActivo() {
        Bono nuevo = bonoDe(null, true);

        assertDoesNotThrow(() -> service.saveConRecurrencia(nuevo, false, null));

        verify(repository).save(nuevo);
    }

    @Test
    void permiteEditarUnBonoExistenteAunqueElFuncionarioYaEstenInactivo() {
        Bono existente = bonoDe(33L, false);
        existente.setMonto(new BigDecimal("120000"));

        assertDoesNotThrow(() -> service.saveConRecurrencia(existente, false, null));

        verify(repository).save(existente);
    }
}
