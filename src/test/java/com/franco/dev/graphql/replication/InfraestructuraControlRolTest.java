package com.franco.dev.graphql.replication;

import com.franco.dev.graphql.empresarial.SucursalGraphQL;
import com.franco.dev.graphql.impresion.CupsAdminGraphQL;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replicacion, tablas de replicacion, impresoras y sucursales: sin el rol, cada
 * mutation se corta antes de tocar un servicio (los servicios quedan en null: sin control, NPE).
 */
class InfraestructuraControlRolTest {

    private static final List<String> REPLICACION = Arrays.asList(
            "setupReplication", "setupReplicationAdvanced", "setupBranchReplication", "removeFullReplication",
            "removeReplication", "removeReplicationAdvanced", "removeBranchReplication", "toggleReplication",
            "refreshSubscription", "refreshAllSubscriptions", "toggleRemoteSubscription", "dropRemotePublication",
            "createRemotePublication", "createRemoteSubscription", "editRemotePublication", "editRemoteSubscription",
            "refreshRemoteSubscription", "refreshAllRemoteSubscriptions", "syncPublicationsWithReplicationTable",
            "alinearSecuenciasFiliales");
    private static final List<String> TABLAS = Arrays.asList(
            "saveReplicationTable", "deleteReplicationTable", "toggleReplicationTableEnabled",
            "updateReplicationServiceTables");
    private static final List<String> CUPS = Arrays.asList(
            "instalarImpresoraCups", "instalarImpresoraSmb", "compartirColaCups", "reactivarCola");

    private static Object con(Object resolver, String... roles) {
        ReflectionTestUtils.setField(resolver, "seg", new TesoreriaSecurityFalso(1L, roles));
        return resolver;
    }

    private static Throwable invocar(Object resolver, String nombre) throws Exception {
        Method m = Arrays.stream(resolver.getClass().getMethods())
                .filter(x -> x.getName().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + nombre));
        Object[] args = Arrays.stream(m.getParameterTypes())
                .map(t -> t == boolean.class ? (Object) false : t == int.class ? (Object) 0 : null)
                .toArray();
        try {
            m.invoke(resolver, args);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    private static void seRechazan(Object resolver, List<String> nombres) throws Exception {
        for (String n : nombres) {
            Throwable t = invocar(resolver, n);
            assertTrue(t instanceof GraphQLException, n + " no se rechazó: " + t);
        }
    }

    @Test
    void replicacion_es_de_admin() throws Exception {
        seRechazan(con(new LogicalReplicationGraphQL(), "SOPORTE", "CONFIGURACION"), REPLICACION);
        seRechazan(con(new ReplicationTableGraphQL(), "SOPORTE", "CONFIGURACION"), TABLAS);
        seRechazan(con(new SucursalGraphQL(), "CONFIGURACION"), List.of("deleteSucursal"));
    }

    @Test
    void impresoras_y_guardar_sucursal_piden_configuracion() throws Exception {
        seRechazan(con(new CupsAdminGraphQL(), "SOPORTE"), CUPS);
        seRechazan(con(new SucursalGraphQL(), "SOPORTE"), List.of("saveSucursal"));

        // Con CONFIGURACION pasa el control y sigue hasta el servicio (null en el test).
        Throwable t = invocar(con(new CupsAdminGraphQL(), "CONFIGURACION"), "reactivarCola");
        assertTrue(t instanceof NullPointerException, "debía llegar al servicio: " + t);
    }

    @Test
    void admin_pasa_el_control() throws Exception {
        Throwable t = invocar(con(new ReplicationTableGraphQL(), "ADMIN"), "toggleReplicationTableEnabled");
        assertFalse(t instanceof GraphQLException, "ADMIN no debía rechazarse: " + t);
    }
}
