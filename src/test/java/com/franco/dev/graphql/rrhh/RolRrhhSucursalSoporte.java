package com.franco.dev.graphql.rrhh;

import com.franco.dev.graphql.financiero.RolCajaSoporte;
import com.franco.dev.repository.rrhh.RrhhAlcanceRepository;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.service.rrhh.RrhhAlcanceService;
import com.franco.dev.service.rrhh.RrhhSecurityService;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Arma los servicios de seguridad reales de RRHH y de tesorería sobre los mismos mocks, para probar el rol
 * acotado RRHH SUCURSAL de punta a punta sin levantar el contexto de Spring.
 */
public class RolRrhhSucursalSoporte {

    public final RolCajaSoporte caja = new RolCajaSoporte();
    public final RrhhAlcanceRepository alcanceRepository = mock(RrhhAlcanceRepository.class);
    public final RrhhSecurityService seg = new RrhhSecurityService();
    public final RrhhAlcanceService alcance = new RrhhAlcanceService();

    public RolRrhhSucursalSoporte() {
        ReflectionTestUtils.setField(seg, "usuarioService", caja.usuarioService);
        ReflectionTestUtils.setField(seg, "roleService", caja.roleService);
        ReflectionTestUtils.setField(alcance, "seg", seg);
        ReflectionTestUtils.setField(alcance, "tesoreria", caja.seg);
        ReflectionTestUtils.setField(alcance, "repository", alcanceRepository);
    }

    public void autenticar(String nickname, String... roles) {
        caja.autenticar(nickname, roles);
    }

    /** Las sucursales que cubren las cajas que el usuario puede mover. */
    public void aCargoDe(Long... sucursales) {
        when(caja.accesoRepository.sucursalesOperablesIds(RolCajaSoporte.USUARIO_ID)).thenReturn(Arrays.asList(sucursales));
    }

    /** Instancia el resolver con los servicios de seguridad reales y el resto de las dependencias en null. */
    public <T> T resolver(Class<T> tipo) {
        try {
            Constructor<?> c = tipo.getDeclaredConstructors()[0];
            c.setAccessible(true);
            Object[] args = Arrays.stream(c.getParameterTypes()).map(this::dependencia).toArray();
            Object r = c.newInstance(args);
            for (Field f : tipo.getDeclaredFields()) {
                Object d = dependencia(f.getType());
                if (d != null) {
                    f.setAccessible(true);
                    f.set(r, d);
                }
            }
            return tipo.cast(r);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object dependencia(Class<?> tipo) {
        if (tipo == RrhhSecurityService.class) return seg;
        if (tipo == RrhhAlcanceService.class) return alcance;
        if (tipo == TesoreriaSecurityService.class) return caja.seg;
        return null;
    }
}
