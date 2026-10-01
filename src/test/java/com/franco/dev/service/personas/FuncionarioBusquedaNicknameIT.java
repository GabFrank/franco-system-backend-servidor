package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * IT de la busqueda "Nombre o nickname" del listado de funcionarios, contra la DB dev real.
 * El nickname vive en personas.usuario (el usuario de la misma persona), no en el funcionario:
 * valida que el JPQL lo mira, que el count derivado de la pagina cuadra y que el OR del texto
 * no se come los demas filtros.
 *
 * NO corre en CI (no hay DB): se activa con -Dit.funcionario=true.
 * @Transactional -> rollback automatico, el nickname de prueba no queda en la DB dev.
 *
 * Correr con perfil dev y la replicacion apagada: una @SpringBootTest sin perfil prende los
 * schedulers que se conectan a las filiales reales.
 *   ./mvnw -Dit.funcionario=true -Dtest=FuncionarioBusquedaNicknameIT test \
 *     -Dspring.profiles.active=dev -Dreplication.sync.enabled=false -Dreplication.refresh.enabled=false
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "it.funcionario", matches = "true")
public class FuncionarioBusquedaNicknameIT {

    private static final String NICK = "ZQXNICKIT";

    @Autowired
    private FuncionarioService service;

    @PersistenceContext
    private EntityManager em;

    private Page<Funcionario> buscar(String nombre, List<Long> sucursales) {
        return service.findAllWithPage(null, nombre, sucursales, null, null, null, null, null,
                PageRequest.of(0, 20));
    }

    @Test
    void encuentraPorNicknameDelUsuario() {
        List<Object[]> candidatos = em.createQuery(
                "select f, u from Funcionario f join f.persona p join Usuario u on u.persona = p " +
                        "where f.sucursal is not null order by f.id", Object[].class)
                .setMaxResults(1)
                .getResultList();
        assumeTrue(!candidatos.isEmpty(), "DB dev sin funcionarios con usuario y sucursal");
        Funcionario funcionario = (Funcionario) candidatos.get(0)[0];
        Usuario usuario = (Usuario) candidatos.get(0)[1];

        usuario.setNickname(NICK);
        em.flush();

        Page<Funcionario> porNick = buscar(NICK, null);
        assertEquals(1, porNick.getContent().size(), "el nickname tiene que encontrar a su funcionario");
        assertEquals(funcionario.getId(), porNick.getContent().get(0).getId());
        assertEquals(1, porNick.getTotalElements(), "el count de la pagina tiene que cuadrar con las filas");

        Long otraSucursal = em.createQuery(
                "select s.id from Sucursal s where s.id <> :id order by s.id", Long.class)
                .setParameter("id", funcionario.getSucursal().getId())
                .setMaxResults(1)
                .getResultList().stream().findFirst().orElse(null);
        assumeTrue(otraSucursal != null, "DB dev con una sola sucursal");
        assertTrue(buscar(NICK, Collections.singletonList(otraSucursal)).getContent().isEmpty(),
                "el match por nickname no puede saltarse el filtro de sucursal");

        String nombre = funcionario.getPersona().getNombre().trim().toUpperCase();
        List<Long> idsPorNombre = buscar(nombre, null).getContent().stream()
                .map(Funcionario::getId).collect(java.util.stream.Collectors.toList());
        assertTrue(idsPorNombre.contains(funcionario.getId()), "la busqueda por nombre sigue andando");
    }
}
