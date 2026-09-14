package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT del filtro "cobra por banco" del listado de funcionarios, contra la DB dev real.
 * Valida que el JPQL con coalesce ejecuta y que los tres estados del filtro
 * (Si / No / Todos) devuelven lo que corresponde.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.funcionario=true.
 * Sin perfil: usa application.properties (bodega@localhost:5551), la misma DB
 * contra la que corre el central en dev.
 * @Transactional -> rollback automatico, no deja el flag prendido en la DB dev.
 *
 * Correr:  ./mvnw -Dit.funcionario=true -Dtest=FuncionarioFiltroCobraBancoIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.funcionario", matches = "true")
public class FuncionarioFiltroCobraBancoIT {

    @Autowired
    private FuncionarioService service;

    @PersistenceContext
    private EntityManager em;

    @Test
    void filtraPorCobraBanco() {
        Page<Funcionario> todosAntes = service.findAllWithPage(null, null, null, true, null, null, null, null,
                PageRequest.of(0, 5));
        assumeTrue(todosAntes.getTotalElements() > 0, "DB dev sin funcionarios activos");

        Funcionario elegido = todosAntes.getContent().get(0);
        elegido.setCobraBanco(true);
        em.flush();

        // Si
        Page<Funcionario> banco = service.findAllWithPage(null, null, null, true, null, null, null, true,
                PageRequest.of(0, 50));
        List<Long> idsBanco = banco.getContent().stream().map(Funcionario::getId).collect(java.util.stream.Collectors.toList());
        assertTrue(idsBanco.contains(elegido.getId()), "el funcionario marcado tiene que aparecer en el filtro Si");

        // No: el mismo no puede estar, y las filas en false/null si
        Page<Funcionario> efectivo = service.findAllWithPage(null, null, null, true, null, null, null, false,
                PageRequest.of(0, 50));
        List<Long> idsEfectivo = efectivo.getContent().stream().map(Funcionario::getId).collect(java.util.stream.Collectors.toList());
        assertFalse(idsEfectivo.contains(elegido.getId()), "el funcionario marcado no puede aparecer en el filtro No");

        // Todos = Si + No, sin perder ninguna fila (el coalesce cubre los null)
        long total = service.findAllWithPage(null, null, null, true, null, null, null, null,
                PageRequest.of(0, 1)).getTotalElements();
        assertEquals(total, banco.getTotalElements() + efectivo.getTotalElements(),
                "Si + No tiene que dar el total de Todos");
    }
}
