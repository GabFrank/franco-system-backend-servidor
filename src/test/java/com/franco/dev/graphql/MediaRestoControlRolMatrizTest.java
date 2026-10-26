package com.franco.dev.graphql;

import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.activos.*;
import com.franco.dev.graphql.configuracion.NotificacionPushGraphQL;
import com.franco.dev.graphql.empresarial.ConfiguracionGeneralGraphQL;
import com.franco.dev.graphql.equipos.EquipoGraphQL;
import com.franco.dev.graphql.financiero.*;
import com.franco.dev.graphql.operaciones.DeliveryGraphQL;
import com.franco.dev.graphql.operaciones.VentaItemGraphQL;
import com.franco.dev.graphql.personas.*;
import com.franco.dev.service.operaciones.compras.ComprasSecurityService;
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

/** Ultima tanda de la lista MEDIA (activos, personas, proveedores y resto), con los helpers de {@link ControlRolMatrizTest}. */
class MediaRestoControlRolMatrizTest {

    static String[] mas(String rol, List<String> otros) {
        List<String> l = new ArrayList<>(otros);
        l.add(0, rol);
        return l.toArray(new String[0]);
    }

    static List<Fila> filas() {
        String[] personas = PersonaGraphQL.ROLES_PERSONAS;
        String[] alta = {"CREAR FUNCIONARIOS", "VER FUNCIONARIOS", "RRHH GESTIONAR"};
        return Arrays.asList(
                f(EnteArchivoGraphQL.class, "saveEnteArchivo"), f(EnteArchivoGraphQL.class, "deleteEnteArchivo"),
                f(EnteGraphQL.class, "saveEnteSucursal"), f(EnteGraphQL.class, "deleteEnte"),
                f(EnteGraphQL.class, "deleteEnteSucursal"),
                f(GpsGraphQL.class, "saveGps"), f(GpsGraphQL.class, "deleteGps"),
                f(GpsGraphQL.class, "enviarComandoGps"), f(GpsGraphQL.class, "guardarConfigAlertasGps"),
                f(InmuebleGraphQL.class, "saveInmueble"), f(InmuebleGraphQL.class, "deleteInmueble"),
                f(MuebleGraphQL.class, "saveMueble"), f(MuebleGraphQL.class, "deleteMueble"),
                f(VehiculoGraphQL.class, "saveVehiculo"), f(VehiculoGraphQL.class, "deleteVehiculo"),
                f(EquipoGraphQL.class, "saveEquipo"), f(EquipoGraphQL.class, "deleteEquipo"),
                f(NotificacionPushGraphQL.class, "enviarNotificacionPersonalizada"),
                f(ConfiguracionGeneralGraphQL.class, "saveConfiguracionGeneral"),
                f(CambioGraphQL.class, "actualizarCotizacionesMercado", mas("CAMBIAR COTIZACION", ComprasSecurityService.ROLES_COMPRAS)),
                f(ConfiguracionFacturaConVentaGraphQL.class, "saveConfiguracionFacturaConVenta", "ANALISIS DE CAJA"),
                f(DocumentoGraphQL.class, "saveDocumento"), f(DocumentoGraphQL.class, "deleteDocumento"),
                f(EnteVinculacionGraphQL.class, "saveEnteVinculacion"), f(EnteVinculacionGraphQL.class, "deleteEnteVinculacion"),
                f(MovimientoPersonasGraphQL.class, "saveMovimientoPersonas"),
                f(MovimientoPersonasGraphQL.class, "deleteMovimientoPersonas"),
                f(DeliveryGraphQL.class, "saveDelivery"), f(DeliveryGraphQL.class, "deleteDelivery"),
                f(VentaItemGraphQL.class, "saveVentaItem"), f(VentaItemGraphQL.class, "deleteVentaItem"),
                f(PersonaGraphQL.class, "savePersona", personas),
                f(PreRegistroFuncionarioGraphQL.class, "savePreRegistroFuncionario", alta),
                f(PreRegistroFuncionarioGraphQL.class, "deletePreRegistroFuncionario"),
                f(ProveedorDiasVisitaGraphQL.class, "saveProveedorDiasVisita"),
                f(ProveedorDiasVisitaGraphQL.class, "deleteProveedorDiasVisita"),
                f(ProveedorGraphQL.class, "saveProveedor", personas), f(ProveedorGraphQL.class, "deleteProveedor"),
                f(ProveedorServicioGraphQL.class, "saveProveedorServicio"),
                f(ProveedorServicioGraphQL.class, "deleteProveedorServicio"),
                f(VendedorGraphQL.class, "saveVendedor"), f(VendedorGraphQL.class, "deleteVendedor"),
                f(VendedorProveedorGraphQL.class, "saveVendedorProveedor"),
                f(VendedorProveedorGraphQL.class, "deleteVendedorProveedor"));
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

    /** Las solicitudes de gasto de la PWA y Android crean el ente sin rol; editarlo es de ADMIN. */
    static EnteGraphQL enteResolver(com.franco.dev.service.activos.EnteService service) {
        EnteGraphQL r = new org.springframework.objenesis.ObjenesisStd().newInstance(EnteGraphQL.class);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "service", service);
        org.springframework.test.util.ReflectionTestUtils.setField(r, "seg", new com.franco.dev.service.financiero.TesoreriaSecurityFalso(1L));
        return r;
    }

    static com.franco.dev.graphql.activos.input.EnteInput ente(Long id) {
        com.franco.dev.graphql.activos.input.EnteInput in = new com.franco.dev.graphql.activos.input.EnteInput();
        in.setId(id);
        in.setTipoEnte(com.franco.dev.domain.activos.enums.TipoEnte.values()[0].name());
        in.setReferenciaId(7L);
        return in;
    }

    @Test
    void saveEnteCreaSinRol() {
        com.franco.dev.service.activos.EnteService service = org.mockito.Mockito.mock(com.franco.dev.service.activos.EnteService.class);
        org.mockito.Mockito.when(service.findByTipoEnteAndReferenciaId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(service.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        assertTrue(enteResolver(service).saveEnte(ente(null)) != null);
        org.mockito.Mockito.verify(service).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveEnteDevuelveElExistenteSinDuplicar() {
        com.franco.dev.service.activos.EnteService service = org.mockito.Mockito.mock(com.franco.dev.service.activos.EnteService.class);
        com.franco.dev.domain.activos.Ente existente = new com.franco.dev.domain.activos.Ente();
        org.mockito.Mockito.when(service.findByTipoEnteAndReferenciaId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L))).thenReturn(java.util.Optional.of(existente));
        assertTrue(enteResolver(service).saveEnte(ente(null)) == existente);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void editarUnEnteSinAdminSeRechaza() {
        com.franco.dev.service.activos.EnteService service = org.mockito.Mockito.mock(com.franco.dev.service.activos.EnteService.class);
        Throwable t = org.junit.jupiter.api.Assertions.assertThrows(graphql.GraphQLException.class, () -> enteResolver(service).saveEnte(ente(5L)));
        assertTrue(esRechazo(t), "no fue rechazo por rol: " + t);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
