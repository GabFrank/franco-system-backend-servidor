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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT del filtro "Cobra IPS" del listado de funcionarios, contra la DB dev real.
 * Fija la semantica del null: cae en "No", como lo muestra el legajo.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.funcionario=true.
 * @Transactional -> rollback automatico, no deja flags cambiados en la DB dev.
 *
 * Correr:  ./mvnw -Dit.funcionario=true -Dtest=FuncionarioFiltroIpsIT test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.funcionario", matches = "true")
public class FuncionarioFiltroIpsIT {

    @Autowired
    private FuncionarioService service;

    @PersistenceContext
    private EntityManager em;

    private long contar(Long id, Boolean ipsActivo) {
        return service.findAllWithPage(id, null, null, true, null, null, null, null, ipsActivo,
                PageRequest.of(0, 1)).getTotalElements();
    }

    @Test
    void filtraPorIpsActivo() {
        Page<Funcionario> activos = service.findAllWithPage(null, null, null, true, null, null, null, null, null,
                PageRequest.of(0, 2));
        assumeTrue(activos.getContent().size() == 2, "DB dev con menos de 2 funcionarios activos");

        Funcionario conIps = activos.getContent().get(0);
        Funcionario sinCargar = activos.getContent().get(1);
        conIps.setIpsActivo(true);
        sinCargar.setIpsActivo(null);
        em.flush();

        assertEquals(1, contar(conIps.getId(), true), "el inscripto tiene que aparecer en Si");
        assertEquals(0, contar(conIps.getId(), false), "el inscripto no puede aparecer en No");
        assertEquals(0, contar(sinCargar.getId(), true), "null no puede aparecer en Si");
        assertEquals(1, contar(sinCargar.getId(), false), "null tiene que caer en No");

        // Todos = Si + No, sin perder ninguna fila
        assertEquals(contar(null, null), contar(null, true) + contar(null, false),
                "Si + No tiene que dar el total de Todos");
    }
}
