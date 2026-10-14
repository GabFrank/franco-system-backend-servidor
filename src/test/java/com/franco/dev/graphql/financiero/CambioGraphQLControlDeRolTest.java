package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.CambioInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La cotizacion la usa todo el sistema para convertir: cargarla exige CAMBIAR COTIZACION y borrarla
 * exige superusuario (issue #326). Se arma con el {@link TesoreriaSecurityService} real para probar
 * el control de punta a punta, no un mock que diga que si.
 */
class CambioGraphQLControlDeRolTest {

    private static final Long USUARIO_ID = 7L;
    private static final Long MONEDA_ID = 2L;

    private CambioService cambioService;
    private MonedaService monedaService;
    private UsuarioService usuarioService;
    private RoleService roleService;
    private CambioGraphQL resolver;
    private Usuario autenticado;

    @BeforeEach
    void setUp() {
        cambioService = mock(CambioService.class);
        monedaService = mock(MonedaService.class);
        usuarioService = mock(UsuarioService.class);
        roleService = mock(RoleService.class);

        TesoreriaSecurityService seg = new TesoreriaSecurityService();
        ReflectionTestUtils.setField(seg, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", roleService);

        resolver = new CambioGraphQL();
        ReflectionTestUtils.setField(resolver, "service", cambioService);
        ReflectionTestUtils.setField(resolver, "monedaService", monedaService);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "seg", seg);

        Moneda moneda = new Moneda();
        moneda.setId(MONEDA_ID);
        when(monedaService.findById(MONEDA_ID)).thenReturn(Optional.of(moneda));
        when(cambioService.save(any(Cambio.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList()));
        autenticado = new Usuario();
        autenticado.setId(USUARIO_ID);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(autenticado));
        List<Role> lista = Arrays.stream(roles).map(n -> { Role r = new Role(); r.setNombre(n); return r; })
                .collect(Collectors.toList());
        when(roleService.findByUsuarioId(USUARIO_ID)).thenReturn(lista);
    }

    private CambioInput input() {
        CambioInput input = new CambioInput();
        input.setMonedaId(MONEDA_ID);
        input.setValorEnGs(7300d);
        return input;
    }

    @Test
    @DisplayName("sin el rol no se carga la cotizacion")
    void sinRolNoGuarda() {
        autenticar("ana", "VENTA TOUCH");

        GraphQLException e = assertThrows(GraphQLException.class, () -> resolver.saveCambio(input(), null));
        assertTrue(e.getMessage().contains("CAMBIAR COTIZACION"));
        verify(cambioService, never()).save(any());
    }

    @Test
    @DisplayName("sin sesion no se carga la cotizacion")
    void sinAutenticacionNoGuarda() {
        assertThrows(GraphQLException.class, () -> resolver.saveCambio(input(), null));
        verify(cambioService, never()).save(any());
    }

    @Test
    @DisplayName("con CAMBIAR COTIZACION se carga")
    void conRolGuarda() {
        autenticar("ana", "cambiar cotizacion ");

        Cambio guardado = resolver.saveCambio(input(), null);

        assertEquals(Double.valueOf(7300d), guardado.getValorEnGs());
        assertEquals(MONEDA_ID, guardado.getMoneda().getId());
    }

    @Test
    @DisplayName("ADMIN carga sin tener el rol")
    void adminGuarda() {
        autenticar("ana", "ADMIN");

        assertDoesNotThrow(() -> resolver.saveCambio(input(), null));
        verify(cambioService, times(1)).save(any(Cambio.class));
    }

    @Test
    @DisplayName("el autor es el autenticado, no el usuarioId que manda el cliente")
    void elAutorEsElAutenticado() {
        autenticar("ana", "CAMBIAR COTIZACION");
        CambioInput input = input();
        input.setUsuarioId(999L);

        resolver.saveCambio(input, null);

        ArgumentCaptor<Cambio> captor = ArgumentCaptor.forClass(Cambio.class);
        verify(cambioService).save(captor.capture());
        assertSame(autenticado, captor.getValue().getUsuario());
        verify(usuarioService, never()).findById(999L);
    }

    @Test
    @DisplayName("una cotizacion ya cargada no se pisa")
    void conIdSeRechaza() {
        autenticar("ana", "CAMBIAR COTIZACION");
        CambioInput input = input();
        input.setId(15L);

        assertThrows(GraphQLException.class, () -> resolver.saveCambio(input, null));
        verify(cambioService, never()).save(any());
    }

    @Test
    @DisplayName("valor nulo, cero o negativo se rechaza")
    void valorInvalidoSeRechaza() {
        autenticar("ana", "CAMBIAR COTIZACION");

        for (Double valor : new Double[]{null, 0d, -1d}) {
            CambioInput input = input();
            input.setValorEnGs(valor);
            assertThrows(GraphQLException.class, () -> resolver.saveCambio(input, null), "valor " + valor);
        }
        verify(cambioService, never()).save(any());
    }

    @Test
    @DisplayName("moneda nula o inexistente se rechaza")
    void monedaInvalidaSeRechaza() {
        autenticar("ana", "CAMBIAR COTIZACION");
        when(monedaService.findById(50L)).thenReturn(Optional.empty());

        for (Long monedaId : new Long[]{null, 50L}) {
            CambioInput input = input();
            input.setMonedaId(monedaId);
            assertThrows(GraphQLException.class, () -> resolver.saveCambio(input, null), "moneda " + monedaId);
        }
        verify(cambioService, never()).save(any());
    }

    @Test
    @DisplayName("borrar una cotizacion es solo de superusuario: el rol no alcanza")
    void borrarConRolSeRechaza() {
        autenticar("ana", "CAMBIAR COTIZACION");

        assertThrows(GraphQLException.class, () -> resolver.deleteCambio(15L));
        verify(cambioService, never()).deleteById(any());
    }

    @Test
    @DisplayName("ADMIN borra")
    void adminBorra() {
        autenticar("ana", "ADMIN");
        when(cambioService.deleteById(15L)).thenReturn(true);

        assertTrue(resolver.deleteCambio(15L));
    }
}
