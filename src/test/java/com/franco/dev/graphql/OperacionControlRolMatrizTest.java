package com.franco.dev.graphql;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.configuracion.NotificacionUsuarioGraphQL;
import com.franco.dev.graphql.financiero.CajaObservacionGraphQL;
import com.franco.dev.graphql.financiero.FacturaCorreoGraphQL;
import com.franco.dev.graphql.financiero.input.CajaObservacionInput;
import com.franco.dev.graphql.general.ContactoGraphQL;
import com.franco.dev.graphql.impresion.FacturaPrintRouterGraphQL;
import com.franco.dev.graphql.impresion.PrintRouterGraphQL;
import com.franco.dev.graphql.operaciones.DevolucionGraphQL;
import com.franco.dev.graphql.operaciones.PedidoGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.graphql.operaciones.VentaObservacionGraphQL;
import com.franco.dev.service.configuracion.NotificacionDestinatarioService;
import com.franco.dev.service.configuracion.NotificacionService;
import com.franco.dev.service.financiero.CajaObservacionService;
import com.franco.dev.service.financiero.TesoreriaSecurityFalso;
import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
import graphql.GraphQLException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.franco.dev.graphql.ControlRolMatrizTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/** Operacion diaria de la lista BAJA: impresion, correo, observaciones, contactos y tablero de notificaciones. */
class OperacionControlRolMatrizTest {

    static final String[] TRANSF = {"VER TRANSFERENCIA", "CREAR TRANSFERENCIA"};

    static List<Fila> filas() {
        String[] compras = ComprasSecurityService.ROLES_COMPRAS.toArray(new String[0]);
        return Arrays.asList(
                f(PrintRouterGraphQL.class, "imprimirEnImpresora"),
                f(PrintRouterGraphQL.class, "imprimirEnDestino", "ANALISIS DE CAJA", "CONFIGURACION"),
                f(PrintRouterGraphQL.class, "imprimirPruebaEnImpresora", "CONFIGURACION"),
                f(FacturaPrintRouterGraphQL.class, "imprimirPdfFacturaEnImpresora", "ANALISIS DE CAJA"),
                f(FacturaPrintRouterGraphQL.class, "imprimirTicketFacturaEnImpresora", "ANALISIS DE CAJA"),
                f(DevolucionGraphQL.class, "imprimirEtiquetasSeparado", TRANSF),
                f(DevolucionGraphQL.class, "imprimirTicketRetiroProveedor", TRANSF),
                f(PedidoGraphQL.class, "imprimirPedidoPDF", compras),
                f(PedidoGraphQL.class, "imprimirPedidoTicket", compras),
                f(VentaGraphQL.class, "reimprimirVenta"),
                f(FacturaCorreoGraphQL.class, "enviarFacturaLegalPorCorreo", "ANALISIS DE CAJA"),
                f(CajaObservacionGraphQL.class, "saveCajaObservacion", "ANALISIS DE CAJA"),
                f(CajaObservacionGraphQL.class, "deleteCajaObservacion"),
                f(VentaObservacionGraphQL.class, "saveVentaObservacion", "ANALISIS DE CAJA", "ANALISIS DE VENTA"),
                f(VentaObservacionGraphQL.class, "deleteVentaObservacion"),
                f(ContactoGraphQL.class, "saveContacto"),
                f(ContactoGraphQL.class, "deleteContacto"));
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

    // --- tablero de notificaciones: destinatario o ADMIN ---

    static NotificacionUsuarioGraphQL notificaciones(Long usuarioSesion, Long creador, boolean destinatario, String... roles) {
        NotificacionUsuarioGraphQL r = new ObjenesisStd().newInstance(NotificacionUsuarioGraphQL.class);
        ReflectionTestUtils.setField(r, "seg", new TesoreriaSecurityFalso(usuarioSesion, roles));
        Notificacion n = new Notificacion();
        if (creador != null) {
            Usuario c = new Usuario();
            c.setId(creador);
            n.setUsuarioCreador(c);
        }
        NotificacionService ns = Mockito.mock(NotificacionService.class);
        Mockito.when(ns.findById(7L)).thenReturn(Optional.of(n));
        NotificacionDestinatarioService ds = Mockito.mock(NotificacionDestinatarioService.class);
        Mockito.when(ds.tieneAcceso(7L, usuarioSesion)).thenReturn(destinatario);
        ReflectionTestUtils.setField(r, "notificacionService", ns);
        ReflectionTestUtils.setField(r, "notificacionDestinatarioService", ds);
        return r;
    }

    static Throwable comentar(NotificacionUsuarioGraphQL r) {
        try {
            r.crearComentarioNotificacion(7L, "hola", null, null);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    void unAjenoNoComentaNiMueveLaNotificacion() {
        NotificacionUsuarioGraphQL r = notificaciones(5L, 9L, false);
        assertTrue(esRechazo(comentar(r)));
        Throwable t = assertThrows(GraphQLException.class, () -> r.cambiarEstadoTableroNotificacion(7L, "VERIFICADO"));
        assertTrue(esRechazo(t));
    }

    @Test
    void elDestinatarioYAdminPasanElControl() {
        assertFalse(esRechazo(comentar(notificaciones(5L, 9L, true))), "destinatario");
        assertFalse(esRechazo(comentar(notificaciones(5L, 9L, false, "ADMIN"))), "ADMIN");
    }

    // --- observaciones: el autor es el de la sesion ---

    @Test
    void elAutorDeLaObservacionEsElDeLaSesion() {
        CajaObservacionGraphQL r = new ObjenesisStd().newInstance(CajaObservacionGraphQL.class);
        ReflectionTestUtils.setField(r, "seg", new TesoreriaSecurityFalso(5L, "ANALISIS DE CAJA"));
        CajaObservacionService service = Mockito.mock(CajaObservacionService.class);
        Mockito.when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        ReflectionTestUtils.setField(r, "service", service);
        CajaObservacionInput in = new CajaObservacionInput();
        in.setUsuarioId(99L);
        CajaObservacion o = r.saveCajaObservacion(in);
        assertEquals(5L, o.getUsuario().getId());
    }
}
