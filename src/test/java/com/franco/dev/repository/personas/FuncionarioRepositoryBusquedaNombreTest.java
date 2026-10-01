package com.franco.dev.repository.personas;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El input "Nombre o nickname" del listado de funcionarios manda el texto como :nombre, y el
 * nickname no es del funcionario: es de personas.usuario, el usuario de la misma persona.
 * La consulta tiene que mirarlo; FuncionarioBusquedaNicknameIT la ejecuta contra la DB dev.
 */
class FuncionarioRepositoryBusquedaNombreTest {

    private String sql() {
        Method metodo = Arrays.stream(FuncionarioRepository.class.getMethods())
                .filter(m -> m.getName().equals("findAllWithFilterAndPage"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe findAllWithFilterAndPage"));
        Query query = metodo.getAnnotation(Query.class);
        assertNotNull(query, "findAllWithFilterAndPage debe declarar @Query");
        return query.value().replaceAll("\\s+", " ");
    }

    @Test
    void elTextoTambienBuscaPorNicknameDelUsuario() {
        String sql = sql();
        assertTrue(sql.contains("exists (select usr.id from Usuario usr where usr.persona = p"),
                "el nickname se busca en el usuario de la persona, sin join que duplique filas");
        assertTrue(sql.contains("upper(usr.nickname) like"), "el texto tiene que compararse con el nickname");
    }
}
