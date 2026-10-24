package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.input.RoleInput;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.graphql.personas.input.UsuarioRoleInput;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Quién puede crear, editar y borrar usuarios, asignar roles y registrar la cara de otro. */
class UsuarioGraphQLControlRolTest {

    private static final Long YO = 50L;
    private static final Long OTRO = 60L;

    private UsuarioService service;
    private UsuarioGraphQL graphQL;
    private Usuario otro;

    @BeforeEach
    void setUp() {
        service = mock(UsuarioService.class);
        graphQL = new UsuarioGraphQL();
        ReflectionTestUtils.setField(graphQL, "service", service);
        ReflectionTestUtils.setField(graphQL, "personaService", mock(PersonaService.class));
        when(service.save(any(Usuario.class), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
        when(service.deleteById(anyLong())).thenReturn(true);
        when(service.getRoles(anyLong())).thenReturn(Collections.emptyList());
        otro = usuario(OTRO, "OTRO");
        otro.setCreadoEn(LocalDateTime.now().minusDays(30));
        when(service.findById(OTRO)).thenReturn(Optional.of(otro));
    }

    private void sesion(Long id, String... roles) {
        ReflectionTestUtils.setField(graphQL, "seg", new TesoreriaSecurityFalso(id, roles));
    }

    private static Usuario usuario(Long id, String nickname) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNickname(nickname);
        u.setActivo(true);
        return u;
    }

    private static UsuarioInput edicion(Long id, String nickname) {
        UsuarioInput input = new UsuarioInput();
        input.setId(id);
        input.setNickname(nickname);
        input.setPassword("nueva");
        input.setActivo(true);
        return input;
    }

    // --- saveUsuario ---

    @Test
    void alta_con_ver_funcionarios_pasa_y_sin_rol_se_rechaza() {
        UsuarioInput alta = edicion(null, "nuevo");
        sesion(YO, "VER FUNCIONARIOS");
        assertNotNull(graphQL.saveUsuario(alta));

        sesion(YO, "VENTA TOUCH");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(alta));
        verify(service, times(1)).save(any(Usuario.class), anyBoolean());
    }

    @Test
    void editar_a_otro_sin_editar_usuarios_se_rechaza() {
        sesion(YO, "VER FUNCIONARIOS");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(edicion(OTRO, "OTRO")));
        verify(service, never()).save(any(Usuario.class), anyBoolean());
    }

    @Test
    void editar_a_otro_con_editar_usuarios_pasa() {
        sesion(YO, "EDITAR USUARIOS");
        assertEquals("nueva", graphQL.saveUsuario(edicion(OTRO, "OTRO")).getPassword());
    }

    @Test
    void cambiar_la_clave_propia_sin_rol_conserva_el_resto() {
        Usuario yo = usuario(YO, "JUAN");
        Persona persona = new Persona();
        persona.setId(9L);
        yo.setPersona(persona);
        Usuario creador = usuario(1L, "CREADOR");
        yo.setUsuario(creador);
        LocalDateTime creado = LocalDateTime.of(2025, 1, 2, 3, 4);
        yo.setCreadoEn(creado);
        when(service.findById(YO)).thenReturn(Optional.of(yo));
        sesion(YO);

        UsuarioInput input = edicion(YO, "OTRO NICK");
        input.setActivo(false);
        input.setPersonaId(null);
        input.setUsuarioId(null);
        input.setCreadoEn(LocalDateTime.now());
        Usuario guardado = graphQL.saveUsuario(input);

        assertEquals("nueva", guardado.getPassword());
        assertEquals("JUAN", guardado.getNickname());
        assertEquals(true, guardado.getActivo());
        assertSame(persona, guardado.getPersona());
        assertSame(creador, guardado.getUsuario());
        assertEquals(creado, guardado.getCreadoEn());
    }

    @Test
    void editar_al_admin_exige_ser_admin() {
        Usuario admin = usuario(70L, "ADMIN");
        when(service.findById(70L)).thenReturn(Optional.of(admin));
        sesion(YO, "EDITAR USUARIOS");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(edicion(70L, "ADMIN")));

        when(service.tieneRol(OTRO, "ADMIN")).thenReturn(true);
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(edicion(OTRO, "OTRO")));
        verify(service, never()).save(any(Usuario.class), anyBoolean());

        sesion(YO, "ADMIN");
        assertNotNull(graphQL.saveUsuario(edicion(70L, "ADMIN")));
    }

    @Test
    void sesion_con_nickname_que_ya_no_existe_se_rechaza() {
        sesion(null, "EDITAR USUARIOS");
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> graphQL.saveUsuario(edicion(OTRO, "OTRO")));
        assertTrue(e.getMessage().contains("iniciar sesión"));
    }

    // --- deleteUsuario ---

    @Test
    void ver_funcionarios_borra_solo_el_usuario_recien_creado_y_sin_roles() {
        sesion(YO, "VER FUNCIONARIOS");
        assertThrows(GraphQLException.class, () -> graphQL.deleteUsuario(OTRO));

        otro.setCreadoEn(LocalDateTime.now().minusMinutes(2));
        when(service.getRoles(OTRO)).thenReturn(Collections.singletonList(new Role()));
        assertThrows(GraphQLException.class, () -> graphQL.deleteUsuario(OTRO));
        verify(service, never()).deleteById(anyLong());

        when(service.getRoles(OTRO)).thenReturn(Collections.emptyList());
        assertTrue(graphQL.deleteUsuario(OTRO));
    }

    @Test
    void editar_usuarios_borra_cualquiera_menos_al_admin() {
        sesion(YO, "EDITAR USUARIOS");
        assertTrue(graphQL.deleteUsuario(OTRO));

        when(service.tieneRol(OTRO, "ADMIN")).thenReturn(true);
        assertThrows(GraphQLException.class, () -> graphQL.deleteUsuario(OTRO));
    }

    // --- saveUsuarioImage ---

    @Test
    void la_cara_de_otro_que_ya_tiene_galeria_es_de_rrhh() throws Exception {
        when(service.tieneGaleriaFacial(OTRO)).thenReturn(true);
        sesion(YO, "VENTA TOUCH");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuarioImage(OTRO, "perfil", "img", null, null));
        verify(service, never()).saveUserImage(anyLong(), any(), any(), any(), any());

        sesion(YO, "RRHH GESTIONAR");
        graphQL.saveUsuarioImage(OTRO, "perfil", "img", null, null);
        verify(service).saveUserImage(eq(OTRO), eq("perfil"), eq("img"), any(), any());
    }

    @Test
    void la_cara_de_otro_sin_galeria_y_la_propia_se_registran_sin_rol() throws Exception {
        // tieneGaleriaFacial no cuenta las fotos "auth" viejas: es lo que decide, no isUserFaceAuth
        when(service.isUserFaceAuth(OTRO)).thenReturn(2);
        when(service.tieneGaleriaFacial(OTRO)).thenReturn(false);
        when(service.tieneGaleriaFacial(YO)).thenReturn(true);
        sesion(YO);
        graphQL.saveUsuarioImage(OTRO, "perfil", "img", null, null);
        graphQL.saveUsuarioImage(YO, "perfil", "img", null, null);
        verify(service, times(2)).saveUserImage(anyLong(), eq("perfil"), any(), any(), any());
    }

    @Test
    void tipo_de_imagen_fuera_de_la_lista_se_rechaza() {
        sesion(YO, "ADMIN");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuarioImage(YO, "../x", "img", null, null));
    }

    // --- roles ---

    @Test
    void asignar_o_quitar_roles_es_solo_de_admin() {
        UsuarioRoleGraphQL roles = new UsuarioRoleGraphQL();
        UsuarioRoleService urService = mock(UsuarioRoleService.class);
        ReflectionTestUtils.setField(roles, "service", urService);
        ReflectionTestUtils.setField(roles, "usuarioService", service);
        ReflectionTestUtils.setField(roles, "roleService", mock(RoleService.class));
        ReflectionTestUtils.setField(roles, "seg", new TesoreriaSecurityFalso(YO, "EDITAR USUARIOS", "SOPORTE"));
        UsuarioRoleInput input = new UsuarioRoleInput();
        input.setUserId(YO);
        input.setRoleId(25L);

        assertThrows(GraphQLException.class, () -> roles.saveUsuarioRole(input));
        assertThrows(GraphQLException.class, () -> roles.saveUsuarioRoleList(List.of(input)));
        assertThrows(GraphQLException.class, () -> roles.deleteUsuarioRole(1L));
        verifyNoInteractions(urService);

        ReflectionTestUtils.setField(roles, "seg", new TesoreriaSecurityFalso(YO, "ADMIN"));
        roles.deleteUsuarioRole(1L);
        verify(urService).deleteById(1L);
    }

    @Test
    void crear_o_borrar_roles_es_de_soporte() {
        RoleGraphQL roles = new RoleGraphQL();
        RoleService roleService = mock(RoleService.class);
        ReflectionTestUtils.setField(roles, "service", roleService);
        ReflectionTestUtils.setField(roles, "usuarioService", service);
        ReflectionTestUtils.setField(roles, "seg", new TesoreriaSecurityFalso(YO, "EDITAR USUARIOS"));

        assertThrows(GraphQLException.class, () -> roles.saveRole(new RoleInput()));
        assertThrows(GraphQLException.class, () -> roles.deleteRole(1L));
        verifyNoInteractions(roleService);

        ReflectionTestUtils.setField(roles, "seg", new TesoreriaSecurityFalso(YO, "SOPORTE"));
        roles.deleteRole(1L);
        verify(roleService).deleteById(1L);
    }

    @Test
    void crear_o_tomar_el_nickname_admin_es_solo_de_admin() {
        sesion(YO, "VER FUNCIONARIOS", "EDITAR USUARIOS");
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(edicion(null, " admin")));
        assertThrows(GraphQLException.class, () -> graphQL.saveUsuario(edicion(OTRO, "ADMIN")));
        verify(service, never()).save(any(Usuario.class), anyBoolean());
    }

    @Test
    void soporte_no_crea_ni_toca_el_rol_admin() {
        RoleGraphQL roles = new RoleGraphQL();
        RoleService roleService = mock(RoleService.class);
        Role admin = new Role();
        admin.setId(25L);
        admin.setNombre("ADMIN");
        when(roleService.findById(25L)).thenReturn(Optional.of(admin));
        ReflectionTestUtils.setField(roles, "service", roleService);
        ReflectionTestUtils.setField(roles, "usuarioService", service);
        ReflectionTestUtils.setField(roles, "seg", new TesoreriaSecurityFalso(YO, "SOPORTE"));

        RoleInput nuevo = new RoleInput();
        nuevo.setNombre("admin ");
        assertThrows(GraphQLException.class, () -> roles.saveRole(nuevo));
        RoleInput renombrar = new RoleInput();
        renombrar.setId(25L);
        renombrar.setNombre("OTRO NOMBRE");
        assertThrows(GraphQLException.class, () -> roles.saveRole(renombrar));
        assertThrows(GraphQLException.class, () -> roles.deleteRole(25L));
        verify(roleService, never()).save(any());
        verify(roleService, never()).deleteById(any());
    }
}
