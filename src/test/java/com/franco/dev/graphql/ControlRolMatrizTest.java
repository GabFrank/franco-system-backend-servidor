package com.franco.dev.graphql;

import com.franco.dev.graphql.financiero.*;
import com.franco.dev.graphql.operaciones.*;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import graphql.GraphQLException;
import graphql.GraphqlErrorException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matriz mutation -> rol exigido. Para cada fila: sin roles, o con roles que no son los suyos, se
 * rechaza; con cada uno de sus roles (y con ADMIN) pasa el control.
 *
 * Rechazo = GraphQLException con "No autorizado". Cualquier otra cosa (NPE de un servicio null, el
 * IllegalArgumentException de ModelMapper con input null, otro error de negocio) es "paso el control".
 * Asi un control puesto dentro de un try que lo envuelva en otro error tambien se detecta.
 */
class ControlRolMatrizTest {

    static final String AC = "ANALISIS DE CAJA";
    static final String VT = "VENTA TOUCH";
    static final String VTC = "VENTA TARJETA COMPLETAR";
    static final String GEST = "TESORERIA GESTIONAR";
    static final String CANC = "CANCELACION DE VENTA";
    static final String VU = "VER USUARIOS";

    /** Roles de negocio que existen; ninguno es ADMIN. */
    static final List<String> TODOS = Arrays.asList(AC, VT, VTC, GEST, CANC, VU, "VER TRANSFERENCIA",
            "CREAR TRANSFERENCIA", "FACTURACION EMITIR", "RRHH GESTIONAR", "RRHH CONFIG", "RRHH VER", "SOPORTE",
            "VER FUNCIONARIOS", "VER PERSONAS", "EDITAR USUARIOS", "TESORERIA CAJA", "RECIBIR PEDIDOS");

    static class Fila {
        final Class<?> clase; final String campo; final String metodo; final List<String> roles;
        Fila(Class<?> clase, String campo, String metodo, String... roles) {
            this.clase = clase; this.campo = campo; this.metodo = metodo; this.roles = Arrays.asList(roles);
        }
        @Override public String toString() { return clase.getSimpleName() + "." + metodo; }
    }

    static Fila f(Class<?> c, String metodo, String... roles) { return new Fila(c, "seg", metodo, roles); }

    static List<Fila> filas() {
        return Arrays.asList(
                // caja y efectivo
                f(CambioCajaGraphQL.class, "saveCambioCaja"), f(CambioCajaGraphQL.class, "deleteCambioCaja"),
                new Fila(ConteoGraphQL.class, "tesoreriaSecurityService", "deleteConteo"),
                f(ConteoMonedaGraphQL.class, "saveConteoMoneda"), f(ConteoMonedaGraphQL.class, "deleteConteoMoneda"),
                f(MaletinGraphQL.class, "saveMaletin"), f(MaletinGraphQL.class, "deleteMaletin"),
                f(MovimientoCajaGraphQL.class, "saveMovimientoCaja"), f(MovimientoCajaGraphQL.class, "deleteMovimientoCaja"),
                f(PdvCajaGraphQL.class, "savePdvCaja", AC, VT), f(PdvCajaGraphQL.class, "savePdvCajaPorSucursal", VT),
                f(PdvCajaGraphQL.class, "abrirCajaDesdeServidor", VT),
                f(RetiroGraphQL.class, "saveRetiro", VT), f(RetiroGraphQL.class, "deleteRetiro"),
                f(RetiroDetalleGraphQL.class, "saveRetiroDetalle"), f(RetiroDetalleGraphQL.class, "deleteRetiroDetalle"),
                f(SencilloGraphQL.class, "saveSencillo"), f(SencilloGraphQL.class, "deleteSencillo"),
                f(SencilloDetalleGraphQL.class, "saveSencilloDetalle"), f(SencilloDetalleGraphQL.class, "deleteSencilloDetalle"),
                f(VueltoGraphQL.class, "saveVuelto"), f(VueltoGraphQL.class, "deleteVuelto"),
                f(VueltoItemGraphQL.class, "saveVueltoItem"), f(VueltoItemGraphQL.class, "deleteVueltoItem"),
                f(GastoGraphQL.class, "saveGasto", VT), f(GastoGraphQL.class, "deleteGasto"),
                f(GastoDetalleGraphQL.class, "saveGastoDetalle"), f(GastoDetalleGraphQL.class, "deleteGastoDetalle"),
                f(GastoRendicionGraphQL.class, "deleteGastoRendicion"),
                f(PreGastoGraphQL.class, "autorizarPreGasto", AC), f(PreGastoGraphQL.class, "rechazarPreGasto", AC),
                f(PreGastoGraphQL.class, "completarPreGasto", AC), f(PreGastoGraphQL.class, "enviarPreGastoATesoreria", AC),
                f(PreGastoGraphQL.class, "registrarDevolucionSaldoPreGasto", AC),
                f(PreGastoGraphQL.class, "tramitarPreGasto"), f(PreGastoGraphQL.class, "deletePreGasto"),
                f(PreGastoGraphQL.class, "ejecutarRetiroPreGasto", VT),
                f(CobroDetalleGraphQL.class, "saveCobroDetalle", VT),
                f(CobroGraphQL.class, "deleteCobro", CANC)
        );
    }

    static Object resolver(Fila fila, String... roles) {
        Object r = new ObjenesisStd().newInstance(fila.clase);
        ReflectionTestUtils.setField(r, fila.campo, new TesoreriaSecurityFalso(1L, roles));
        return r;
    }

    static Throwable invocar(Object r, String nombre) throws Exception {
        Method m = Arrays.stream(r.getClass().getMethods()).filter(x -> x.getName().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("no existe " + nombre));
        Object[] args = Arrays.stream(m.getParameterTypes()).map(t ->
                t == boolean.class ? (Object) false : t == int.class ? (Object) 0 : t == long.class ? (Object) 0L
                        : t == double.class ? (Object) 0d : null).toArray();
        try {
            m.invoke(r, args);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    static boolean esRechazo(Throwable t) {
        return t instanceof GraphQLException && !(t instanceof GraphqlErrorException)
                && t.getMessage() != null && t.getMessage().startsWith("No autorizado");
    }

    @TestFactory
    Stream<DynamicTest> cadaMutationExigeExactamenteSusRoles() {
        return filas().stream().map(fila -> DynamicTest.dynamicTest(fila.toString(), () -> {
            assertTrue(esRechazo(invocar(resolver(fila), fila.metodo)), fila + ": sin roles no se rechazo");
            List<String> ajenos = new ArrayList<>(TODOS);
            ajenos.removeAll(fila.roles);
            Throwable conAjenos = invocar(resolver(fila, ajenos.toArray(new String[0])), fila.metodo);
            assertTrue(esRechazo(conAjenos), fila + ": con roles ajenos no se rechazo: " + conAjenos);
            for (String rol : fila.roles) {
                Throwable t = invocar(resolver(fila, rol), fila.metodo);
                assertTrue(!esRechazo(t), fila + ": con " + rol + " se rechazo");
            }
            assertTrue(!esRechazo(invocar(resolver(fila, "ADMIN"), fila.metodo)), fila + ": ADMIN se rechazo");
        }));
    }
}
