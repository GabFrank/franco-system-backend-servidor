package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.PersonaRepository;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.utils.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceFiltroRolTest {

    private UsuarioRepository repository;
    private UsuarioService service;

    @BeforeEach
    void setUp() {
        repository = mock(UsuarioRepository.class);
        service = new UsuarioService(repository, mock(RoleRepository.class), mock(UsuarioRoleService.class),
                mock(RoleService.class), mock(PersonaRepository.class), mock(ImageService.class),
                mock(UsuarioEmbeddingCacheService.class), mock(EmbeddingGaleriaService.class));

        Page<Usuario> vacia = new PageImpl<>(Collections.emptyList());
        when(repository.findbyIdOrPersonaPaginated(anyString(), any(Pageable.class))).thenReturn(vacia);
        when(repository.findbyIdOrPersonaAndRolesPaginated(anyString(), any(), any(Pageable.class))).thenReturn(vacia);
    }

    @Test
    void sinRolesUsaLaBusquedaDeSiempre() {
        service.findbyIdOrPersonaPaginated("juan", 0, 15, null);

        verify(repository).findbyIdOrPersonaPaginated(eq("JUAN"), any(Pageable.class));
        verify(repository, never()).findbyIdOrPersonaAndRolesPaginated(anyString(), any(), any(Pageable.class));
    }

    @Test
    void listaDeRolesVaciaEsSinFiltro() {
        service.findbyIdOrPersonaPaginated("juan", 0, 15, Collections.emptyList());

        verify(repository).findbyIdOrPersonaPaginated(eq("JUAN"), any(Pageable.class));
        verify(repository, never()).findbyIdOrPersonaAndRolesPaginated(anyString(), any(), any(Pageable.class));
    }

    @Test
    void conRolesUsaLaQueryFiltradaConElTextoEnMayusculas() {
        List<Long> roles = Arrays.asList(3L, 8L);

        service.findbyIdOrPersonaPaginated("juan perez", 0, 15, roles);

        verify(repository).findbyIdOrPersonaAndRolesPaginated(eq("JUAN%PEREZ"), eq(roles), any(Pageable.class));
        verify(repository, never()).findbyIdOrPersonaPaginated(anyString(), any(Pageable.class));
    }

    @Test
    void conRolesNoTomaElAtajoPorPersonaId() {
        Usuario usuario = new Usuario();
        usuario.setId(70L);
        when(repository.findByPersonaId(anyLong())).thenReturn(usuario);
        List<Long> roles = Collections.singletonList(3L);

        Page<Usuario> resultado = service.findbyIdOrPersonaPaginated("7", 0, 15, roles);

        verify(repository, never()).findByPersonaId(anyLong());
        verify(repository).findbyIdOrPersonaAndRolesPaginated(eq("7"), eq(roles), any(Pageable.class));
        assertEquals(0, resultado.getTotalElements());
    }

    @Test
    void sinRolesElAtajoPorPersonaIdSigueIgual() {
        Usuario usuario = new Usuario();
        usuario.setId(70L);
        when(repository.findByPersonaId(7L)).thenReturn(usuario);

        Page<Usuario> resultado = service.findbyIdOrPersonaPaginated("7", 0, 15);

        assertEquals(1, resultado.getTotalElements());
        assertEquals(70L, resultado.getContent().get(0).getId());
    }

    /**
     * Sin parentesis alrededor del bloque de OR, el "and exists" solo filtraria la ultima condicion
     * (AND tiene mas precedencia que OR) y el filtro por rol dejaria pasar casi todos los usuarios.
     */
    @Test
    void laQueryFiltradaEncierraLosOrEntreParentesisAntesDelExists() throws Exception {
        Method metodo = UsuarioRepository.class.getMethod("findbyIdOrPersonaAndRolesPaginated",
                String.class, List.class, Pageable.class);
        Query query = metodo.getAnnotation(Query.class);
        assertNotNull(query, "findbyIdOrPersonaAndRolesPaginated debe declarar @Query");

        assertOrEntreParentesis(query.value(), "value");
        assertOrEntreParentesis(query.countQuery(), "countQuery");
    }

    private static void assertOrEntreParentesis(String jpql, String cual) {
        String normalizada = jpql.replaceAll("\\s+", " ");
        int where = normalizada.indexOf("where (");
        int exists = normalizada.indexOf(") and exists (");
        assertTrue(where >= 0, cual + ": el where tiene que abrir parentesis: " + normalizada);
        assertTrue(exists > where, cual + ": el bloque de OR tiene que cerrarse antes del 'and exists': " + normalizada);
        String bloque = normalizada.substring(where + "where (".length(), exists);
        assertTrue(bloque.contains(" or "), cual + ": el bloque entre parentesis tiene que ser el de los OR");
    }
}
