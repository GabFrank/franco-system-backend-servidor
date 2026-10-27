package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.empresarial.ConfiguracionSistemaRepository;
import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import com.franco.dev.service.personas.RoleService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfiguracionSistemaServiceTest {

    private final Map<String, ConfiguracionSistema> bd = new HashMap<>();
    private final ConfiguracionSistemaRepository repo = mock(ConfiguracionSistemaRepository.class);
    private final RoleService roleService = mock(RoleService.class);
    private final ComprasSecurityService seguridad = mock(ComprasSecurityService.class);

    private ConfiguracionSistemaService servicio(String masterKey) {
        when(repo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(bd.get(i.getArgument(0))));
        when(repo.save(any(ConfiguracionSistema.class))).thenAnswer(i -> {
            ConfiguracionSistema c = i.getArgument(0);
            bd.put(c.getClave(), c);
            return c;
        });
        ConfiguracionSistema k = new ConfiguracionSistema();
        k.setClave(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY);
        k.setEncriptado(true);
        bd.put(k.getClave(), k);
        return new ConfiguracionSistemaService(repo, roleService, seguridad, masterKey, "http://localhost:1");
    }

    private void comoUsuarioConRol(String rol) {
        Usuario u = new Usuario();
        u.setId(7L);
        when(seguridad.usuarioActual()).thenReturn(u);
        Role r = new Role();
        r.setNombre(rol);
        when(roleService.findByUsuarioId(7L)).thenReturn(Collections.singletonList(r));
    }

    @Test
    void configuracionIaNuncaDevuelveLaClave() {
        ConfiguracionSistemaService s = servicio("master");
        s.guardar(ConfiguracionSistemaService.CLAVE_OPENAI_API_KEY, "sk-proj-1234AbCd");
        assertNotEquals("sk-proj-1234AbCd", bd.get("openai.api_key").getValor());
        assertNotNull(bd.get("openai.api_key").getModificadoEn());
        ConfiguracionIa ia = s.configuracionIa();
        assertTrue(ia.getClaveCargada());
        assertEquals("…AbCd", ia.getClaveFinal());
        assertEquals("sk-proj-1234AbCd", s.valor("openai.api_key").get());
    }

    @Test
    void sinMasterKeyNoGuardaNiLee() {
        ConfiguracionSistemaService s = servicio("");
        assertFalse(s.configuracionIa().getCifradoDisponible());
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> s.guardar("openai.api_key", "sk-x"));
        assertTrue(e.getMessage().contains("CONFIG_MASTER_KEY"));
    }

    @Test
    void exigirAdminRechazaSinRol() {
        ConfiguracionSistemaService s = servicio("master");
        comoUsuarioConRol("RECIBIR PEDIDOS");
        assertThrows(GraphQLException.class, s::exigirAdmin);
        assertThrows(GraphQLException.class, () -> s.guardarConfiguracionIa("sk-x", null, null));
        assertThrows(GraphQLException.class, s::probarConexionIa);
    }

    @Test
    void exigirAdminAceptaAdmin() {
        ConfiguracionSistemaService s = servicio("master");
        comoUsuarioConRol("admin");
        assertDoesNotThrow(s::exigirAdmin);
    }

    @Test
    void guardarConfiguracionIaRespetaNullVacioYModeloEnBlanco() {
        ConfiguracionSistemaService s = servicio("master");
        comoUsuarioConRol("ADMIN");
        s.guardarConfiguracionIa("sk-proj-wxyz", "gpt-4o-mini", "usar guaraníes");
        assertEquals("gpt-4o-mini", bd.get("openai.modelo").getValor());
        // null no cambia la clave; modelo en blanco vuelve a gpt-4o
        ConfiguracionIa ia = s.guardarConfiguracionIa(null, "  ", null);
        assertEquals("…wxyz", ia.getClaveFinal());
        assertEquals("gpt-4o", ia.getModelo());
        assertEquals("usar guaraníes", ia.getPromptAdicional());
        // "" borra
        ia = s.guardarConfiguracionIa("", null, null);
        assertFalse(ia.getClaveCargada());
        assertNull(ia.getClaveFinal());
    }

    @Test
    void guardaProveedorYClaveDeClaudeSinDevolverla() {
        ConfiguracionSistemaService s = servicio("master");
        ConfiguracionSistema k = new ConfiguracionSistema();
        k.setClave(ConfiguracionSistemaService.CLAVE_ANTHROPIC_API_KEY);
        k.setEncriptado(true);
        bd.put(k.getClave(), k);
        comoUsuarioConRol("ADMIN");

        ConfiguracionIa ia = s.configuracionIa();
        assertEquals("OPENAI", ia.getProveedor());
        assertFalse(ia.getClaudeClaveCargada());
        assertEquals("claude-opus-5-5", ia.getClaudeModelo());

        ia = s.guardarConfiguracionIa(null, null, null, "claude", "sk-ant-api03-XyZw", " ");
        assertEquals("CLAUDE", ia.getProveedor());
        assertTrue(ia.getClaudeClaveCargada());
        assertEquals("…XyZw", ia.getClaudeClaveFinal());
        assertEquals("claude-opus-5-5", ia.getClaudeModelo());
        assertFalse(ia.getClaveCargada());
        assertNotEquals("sk-ant-api03-XyZw", bd.get("anthropic.api_key").getValor());

        assertEquals("OPENAI", s.guardarConfiguracionIa(null, null, null, "otro", null, null).getProveedor());
    }

    @Test
    void probarConexionClaudeSinClaveAvisa() {
        ConfiguracionSistemaService s = servicio("master");
        comoUsuarioConRol("ADMIN");
        GraphQLException e = assertThrows(GraphQLException.class, () -> s.probarConexionIa("CLAUDE"));
        assertTrue(e.getMessage().contains("Claude"));
    }
}
