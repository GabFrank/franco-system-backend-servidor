package com.franco.dev.graphql;

import com.franco.dev.domain.operaciones.enums.DevolucionEstado;
import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.operaciones.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.franco.dev.graphql.ControlRolMatrizTest.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matriz de transferencias, devoluciones y hoja de ruta, con los mismos helpers que {@link ControlRolMatrizTest}. */
class TransferenciasControlRolMatrizTest {

    static final String VER = "VER TRANSFERENCIA";
    static final String CREAR = "CREAR TRANSFERENCIA";

    static List<Fila> filas() {
        return Arrays.asList(
                f(TransferenciaGraphQL.class, "saveTransferencia", VER, CREAR),
                f(TransferenciaGraphQL.class, "deleteTransferencia", VER, CREAR),
                f(TransferenciaGraphQL.class, "finalizarTransferencia", VER, CREAR),
                f(TransferenciaGraphQL.class, "avanzarEtapaTransferencia", VER, CREAR),
                f(TransferenciaGraphQL.class, "verificarParaTransporteMobile", VER, CREAR),
                f(TransferenciaItemGraphQL.class, "saveTransferenciaItem", VER, CREAR),
                f(TransferenciaItemGraphQL.class, "deleteTransferenciaItem", VER, CREAR),
                f(TransferenciaItemGraphQL.class, "desconfirmarTransferenciaItem", VER, CREAR),
                f(TransferenciaItemGraphQL.class, "verificarProducto"),
                f(ConfiguracionTransferenciaGraphQL.class, "saveConfiguracionTransferencia"),
                f(HojaRutaGraphQL.class, "saveHojaRuta", VER, CREAR),
                f(HojaRutaGraphQL.class, "deleteHojaRuta"),
                f(DevolucionGraphQL.class, "crearDevolucion"),
                f(DevolucionGraphQL.class, "confirmarDevolucion"),
                f(DevolucionGraphQL.class, "cancelarDevolucion", VER, CREAR),
                // con estado null (no SEPARADO) exige rol
                f(DevolucionGraphQL.class, "avanzarEstadoDevolucion", VER, CREAR),
                f(DevolucionGraphQL.class, "revertirEstadoDevolucion", VER, CREAR),
                f(DevolucionGraphQL.class, "revertirRetiroDevolucion", VER, CREAR),
                f(DevolucionGraphQL.class, "revertirColectaDevolucion", VER, CREAR),
                f(DevolucionGraphQL.class, "retirarDevolucionesEnBloque", VER, CREAR),
                f(DevolucionGraphQL.class, "colectarDevolucion", VER, CREAR),
                f(DevolucionGraphQL.class, "colectarDevolucionesEnBloque", VER, CREAR),
                f(DevolucionConfiguracionGraphQL.class, "guardarDevolucionConfiguracion"));
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
                assertTrue(!esRechazo(invocar(resolver(fila, rol), fila.metodo)), fila + ": con " + rol + " se rechazo");
            }
            assertTrue(!esRechazo(invocar(resolver(fila, "ADMIN"), fila.metodo)), fila + ": ADMIN se rechazo");
        }));
    }

    /** La carga de devoluciones la hace el personal de sucursal sin rol (decision de la PWA). */
    @TestFactory
    Stream<DynamicTest> laCargaDeDevolucionesNoPideRol() {
        return Stream.of(
                f(DevolucionGraphQL.class, "saveDevolucion"),
                f(DevolucionItemGraphQL.class, "saveDevolucionItem"),
                f(DevolucionItemGraphQL.class, "deleteDevolucionItem")
        ).map(fila -> DynamicTest.dynamicTest(fila.toString(), () -> {
            Object r = new org.springframework.objenesis.ObjenesisStd().newInstance(fila.clase);
            assertFalse(esRechazo(invocar(r, fila.metodo)), fila + " se rechazo sin roles");
        }));
    }

    @Test
    void separarUnaDevolucionNoPideRol() throws Exception {
        Fila fila = f(DevolucionGraphQL.class, "avanzarEstadoDevolucion");
        Object r = resolver(fila);
        java.lang.reflect.Method m = DevolucionGraphQL.class.getMethod("avanzarEstadoDevolucion", Long.class, DevolucionEstado.class, Long.class);
        Throwable t;
        try {
            m.invoke(r, 1L, DevolucionEstado.SEPARADO, 1L);
            t = null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            t = e.getCause();
        }
        assertFalse(esRechazo(t), "separar se rechazo sin roles: " + t);
        try {
            m.invoke(r, 1L, DevolucionEstado.RETIRADO, 1L);
            t = null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            t = e.getCause();
        }
        assertTrue(esRechazo(t), "retirar sin roles no se rechazo: " + t);
    }

    @Test
    void elAltaSinRolNoTraeDatosDeAcreditacion() {
        DevolucionGraphQL r = new org.springframework.objenesis.ObjenesisStd().newInstance(DevolucionGraphQL.class);
        com.franco.dev.service.operaciones.DevolucionService service = org.mockito.Mockito.mock(com.franco.dev.service.operaciones.DevolucionService.class);
        org.mockito.Mockito.when(service.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        org.springframework.test.util.ReflectionTestUtils.setField(r, "service", service);
        com.franco.dev.graphql.operaciones.input.DevolucionInput in = new com.franco.dev.graphql.operaciones.input.DevolucionInput();
        in.setNroNotaCredito("001-001-0000001");
        in.setMontoAcreditado(500000d);
        in.setResolucion(com.franco.dev.domain.operaciones.enums.TipoResolucionDevolucion.values()[0]);
        com.franco.dev.domain.operaciones.Devolucion d = r.saveDevolucion(in);
        org.junit.jupiter.api.Assertions.assertNull(d.getNroNotaCredito());
        org.junit.jupiter.api.Assertions.assertNull(d.getMontoAcreditado());
        org.junit.jupiter.api.Assertions.assertNull(d.getResolucion());
        org.junit.jupiter.api.Assertions.assertEquals(DevolucionEstado.PENDIENTE, d.getEstado());
    }
}
