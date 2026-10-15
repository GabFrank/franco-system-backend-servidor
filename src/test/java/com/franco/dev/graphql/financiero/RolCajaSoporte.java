package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.CajaVirtualAccesoRepository;
import com.franco.dev.repository.financiero.CajaVirtualRepository;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Arma el {@link TesoreriaSecurityService} real sobre mocks y deja llamar un resolver por nombre de
 * método, para probar el control de rol de punta a punta sin levantar el contexto de Spring.
 */
public class RolCajaSoporte {

    public static final Long USUARIO_ID = 77L;

    public final UsuarioService usuarioService = mock(UsuarioService.class);
    public final RoleService roleService = mock(RoleService.class);
    public final CajaVirtualAccesoRepository accesoRepository = mock(CajaVirtualAccesoRepository.class);
    public final CajaVirtualRepository cajaVirtualRepository = mock(CajaVirtualRepository.class);
    public final TesoreriaSecurityService seg = new TesoreriaSecurityService();

    public RolCajaSoporte() {
        ReflectionTestUtils.setField(seg, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", roleService);
        ReflectionTestUtils.setField(seg, "accesoRepository", accesoRepository);
        ReflectionTestUtils.setField(seg, "cajaVirtualRepository", cajaVirtualRepository);
    }

    public Usuario autenticar(String nickname, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nickname, null, Collections.emptyList()));
        Usuario u = new Usuario();
        u.setId(USUARIO_ID);
        when(usuarioService.findByNickname(nickname)).thenReturn(Optional.of(u));
        List<Role> lista = Arrays.stream(roles).map(n -> { Role r = new Role(); r.setNombre(n); return r; })
                .collect(Collectors.toList());
        when(roleService.findByUsuarioId(USUARIO_ID)).thenReturn(lista);
        return u;
    }

    public static void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Instancia el resolver con este servicio de seguridad y el resto de las dependencias en null. */
    public <T> T resolver(Class<T> tipo) {
        try {
            Constructor<?> c = tipo.getDeclaredConstructors()[0];
            c.setAccessible(true);
            Object[] args = Arrays.stream(c.getParameterTypes())
                    .map(p -> p == TesoreriaSecurityService.class ? seg : null).toArray();
            Object r = c.newInstance(args);
            for (Field f : tipo.getDeclaredFields()) {
                if (f.getType() == TesoreriaSecurityService.class) {
                    f.setAccessible(true);
                    f.set(r, seg);
                }
            }
            return tipo.cast(r);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Llama el método con argumentos vacíos y devuelve lo que lanzó, o null. El control de rol es la
     * primera línea: si lo pasa, lo que venga después (un NPE por las dependencias en null) no importa.
     */
    public static Throwable llamar(Object resolver, String metodo) {
        Method m = Arrays.stream(resolver.getClass().getDeclaredMethods())
                .filter(x -> x.getName().equals(metodo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe " + metodo));
        m.setAccessible(true);
        Object[] args = Arrays.stream(m.getParameterTypes()).map(RolCajaSoporte::vacio).toArray();
        try {
            m.invoke(resolver, args);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean esNoAutorizado(Throwable t) {
        return t instanceof graphql.GraphQLException && t.getMessage() != null
                && t.getMessage().startsWith("No autorizado");
    }

    private static Object vacio(Class<?> tipo) {
        if (!tipo.isPrimitive()) return null;
        if (tipo == boolean.class) return false;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == double.class) return 0d;
        throw new IllegalArgumentException("Primitivo sin valor vacío: " + tipo);
    }
}
