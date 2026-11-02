package com.franco.dev.graphql;

import com.franco.dev.graphql.financiero.*;
import com.franco.dev.graphql.operaciones.*;
import com.franco.dev.service.financiero.FacturacionSecurityFalso;
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
    static final String EMITIR = "FACTURACION EMITIR";

    /** Roles de negocio que existen; ninguno es ADMIN. */
    static final List<String> TODOS = Arrays.asList(AC, VT, VTC, GEST, CANC, VU, "VER TRANSFERENCIA",
            "CREAR TRANSFERENCIA", "FACTURACION EMITIR", "RRHH GESTIONAR", "RRHH CONFIG", "RRHH VER", "SOPORTE",
            "VER FUNCIONARIOS", "VER PERSONAS", "EDITAR USUARIOS", "TESORERIA CAJA", "RECIBIR PEDIDOS",
            "TESORERIA VER", "ANALISIS FINANCIERO", "ANALISIS CONTABLE", "VER PRECIO COSTO",
            "EDITAR PRODUCTOS", "CREAR PRECIOS", "EDITAR PRECIOS", "CREAR FUNCIONARIOS", "EDITAR FUNCIONARIOS",
            "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO");

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
                f(RetiroGraphQL.class, "saveRetiro"), f(RetiroGraphQL.class, "deleteRetiro"),
                f(RetiroDetalleGraphQL.class, "saveRetiroDetalle"), f(RetiroDetalleGraphQL.class, "deleteRetiroDetalle"),
                f(SencilloGraphQL.class, "saveSencillo"), f(SencilloGraphQL.class, "deleteSencillo"),
                f(SencilloDetalleGraphQL.class, "saveSencilloDetalle"), f(SencilloDetalleGraphQL.class, "deleteSencilloDetalle"),
                f(VueltoGraphQL.class, "saveVuelto"), f(VueltoGraphQL.class, "deleteVuelto"),
                f(VueltoItemGraphQL.class, "saveVueltoItem"), f(VueltoItemGraphQL.class, "deleteVueltoItem"),
                f(GastoGraphQL.class, "saveGasto"), f(GastoGraphQL.class, "deleteGasto"),
                f(GastoDetalleGraphQL.class, "saveGastoDetalle"), f(GastoDetalleGraphQL.class, "deleteGastoDetalle"),
                f(GastoRendicionGraphQL.class, "deleteGastoRendicion"),
                f(PreGastoGraphQL.class, "autorizarPreGasto", AC), f(PreGastoGraphQL.class, "rechazarPreGasto", AC),
                f(PreGastoGraphQL.class, "completarPreGasto", AC), f(PreGastoGraphQL.class, "enviarPreGastoATesoreria", AC),
                f(PreGastoGraphQL.class, "registrarDevolucionSaldoPreGasto", AC),
                f(PreGastoGraphQL.class, "tramitarPreGasto"), f(PreGastoGraphQL.class, "deletePreGasto"),
                f(PreGastoGraphQL.class, "ejecutarRetiroPreGasto", VT),
                f(CobroDetalleGraphQL.class, "saveCobroDetalle"),
                f(CobroGraphQL.class, "deleteCobro", CANC),
                // fiscal
                f(DocumentoElectronicoGraphQL.class, "saveDocumentoElectronico"),
                f(DocumentoElectronicoGraphQL.class, "crearDocumentoElectronicoDesdeFactura"),
                new Fila(EventosSifenGraphQL.class, "facturacionSeg", "nominarReceptorDocumento", EMITIR),
                f(EventosSifenGraphQL.class, "inutilizarNumeros", AC),
                f(FacturaLegalGraphQL.class, "deleteFacturaLegal"),
                f(FacturaLegalGraphQL.class, "updateFacturaLegal", AC),
                f(FacturaLegalGraphQL.class, "saveFacturaLegalToFilial", AC),
                f(LoteDEGraphQL.class, "saveLoteDe"), f(LoteDEGraphQL.class, "crearLote"),
                f(LoteDEGraphQL.class, "vincularDocumentosALote"), f(LoteDEGraphQL.class, "enviarLote"),
                f(LoteDEGraphQL.class, "consultarLote"), f(DocumentoElectronicoGraphQL.class, "consultarDocumentoElectronico"),
                f(TimbradoGraphQL.class, "saveTimbrado"), f(TimbradoGraphQL.class, "deleteTimbrado"),
                f(TimbradoDetalleGraphQL.class, "saveTimbradoDetalle"), f(TimbradoDetalleGraphQL.class, "deleteTimbradoDetalle"),
                // configuracion financiera, credito, tarjeta y cobros
                f(BancoGraphQL.class, "saveBanco", GEST), f(BancoGraphQL.class, "deleteBanco", GEST),
                f(FormaPagoGraphQL.class, "saveFormaPago"), f(FormaPagoGraphQL.class, "deleteFormaPago"),
                f(MonedaGraphQL.class, "saveMoneda", GEST), f(MonedaGraphQL.class, "deleteMoneda", GEST),
                f(MonedaBilletesGraphQL.class, "saveMonedaBillete"), f(MonedaBilletesGraphQL.class, "deleteMonedaBillete"),
                f(EnteCuotaGraphQL.class, "saveEnteCuota"), f(EnteCuotaGraphQL.class, "deleteEnteCuota"),
                f(EnteFinancieroGraphQL.class, "saveEnteFinanciero"), f(EnteFinancieroGraphQL.class, "deleteEnteFinanciero"),
                f(VentaCreditoGraphQL.class, "saveVentaCredito"), f(VentaCreditoGraphQL.class, "deleteVentaCredito"),
                f(VentaCreditoGraphQL.class, "finalizarVentaCredito"), f(VentaCreditoGraphQL.class, "finalizarVentaCreditos", VU),
                f(VentaCreditoCuotasGraphQL.class, "saveVentaCreditoCuota"), f(VentaCreditoCuotasGraphQL.class, "deleteVentaCreditoCuota"),
                f(VentaTarjetaGraphQL.class, "saveVentaTarjeta", VT, VTC), f(VentaTarjetaGraphQL.class, "updateVentaTarjeta", VT, VTC),
                f(VentaTarjetaGraphQL.class, "deleteVentaTarjeta"),
                f(VentaTarjetaGraphQL.class, "cancelarVentaTarjetaPorVentaId", CANC),
                f(CobroDetalleGraphQL.class, "deleteCobroDetalle"),
                f(VentaGraphQL.class, "deleteVenta", CANC), f(VentaGraphQL.class, "saveVenta"),
                // devoluciones y funcionarios
                f(DevolucionGraphQL.class, "acreditarDevolucion", "VER TRANSFERENCIA"),
                f(NotaCreditoDevolucionGraphQL.class, "acreditarRetiro", "VER TRANSFERENCIA"),
                f(NotaCreditoDevolucionGraphQL.class, "revertirNotaCreditoDevolucion", "VER TRANSFERENCIA"),
                // input null = alta; la edicion (EDITAR FUNCIONARIOS / RRHH GESTIONAR) la cubre FuncionarioPagaRolTest
                f(com.franco.dev.graphql.personas.FuncionarioGraphQL.class, "saveFuncionario", "CREAR FUNCIONARIOS",
                        "VER FUNCIONARIOS", "RRHH GESTIONAR"),
                f(com.franco.dev.graphql.personas.FuncionarioGraphQL.class, "deleteFuncionario"),
                // precios y costos
                f(com.franco.dev.graphql.productos.PrecioPorSucursalGraphQL.class, "savePrecioPorSucursal", "CREAR PRECIOS", "EDITAR PRECIOS"),
                f(com.franco.dev.graphql.productos.PrecioPorSucursalGraphQL.class, "deletePrecioPorSucursal", "CREAR PRECIOS", "EDITAR PRECIOS"),
                f(com.franco.dev.graphql.productos.CostoPorProductoGraphQL.class, "saveCostoPorProducto", "EDITAR PRODUCTOS"),
                f(com.franco.dev.graphql.productos.CostoPorProductoGraphQL.class, "deleteCostoPorProducto"),
                f(com.franco.dev.graphql.productos.TipoPrecioGraphQL.class, "saveTipoPrecio"),
                f(com.franco.dev.graphql.productos.TipoPrecioGraphQL.class, "deleteTipoPrecio"),
                f(ProgramarPrecioGraphQL.class, "saveProgramarPrecio"), f(ProgramarPrecioGraphQL.class, "deleteProgramarPrecio"),
                f(PrecioDeliveryGraphQL.class, "savePrecioDelivery"), f(PrecioDeliveryGraphQL.class, "deletePrecioDelivery"),
                // clientes y personas
                f(com.franco.dev.graphql.personas.ClienteGraphQL.class, "saveCliente", VU, "VER PERSONAS"),
                f(com.franco.dev.graphql.personas.ClienteGraphQL.class, "deleteCliente"),
                f(com.franco.dev.graphql.personas.ClienteAdicionalGraphQL.class, "saveClienteAdicional"),
                f(com.franco.dev.graphql.personas.ClienteAdicionalGraphQL.class, "deleteClienteAdicional"),
                f(com.franco.dev.graphql.personas.PersonaGraphQL.class, "deletePersona", "CREAR FUNCIONARIOS",
                        "VER FUNCIONARIOS", "RRHH GESTIONAR"),
                // stock e inventario (tanda 2)
                f(com.franco.dev.graphql.operaciones.MovimientoStockLoteGraphQL.class, "ajustarStockLote", "EDITAR PRODUCTOS"),
                f(com.franco.dev.graphql.operaciones.MovimientoStockLoteGraphQL.class, "cambiarEstadoLote", "EDITAR PRODUCTOS"),
                f(com.franco.dev.graphql.operaciones.MovimientoStockLoteGraphQL.class, "crearLoteProducto", "EDITAR PRODUCTOS", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.MovimientoStockLoteGraphQL.class, "actualizarFechasLote", "EDITAR PRODUCTOS", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.MovimientoGraphQL.class, "saveMovimientoStock", "EDITAR PRODUCTOS"),
                f(com.franco.dev.graphql.operaciones.MovimientoGraphQL.class, "deleteMovimientoStock"),
                f(com.franco.dev.graphql.operaciones.InventarioGraphQL.class, "saveInventario", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioGraphQL.class, "deleteInventario"),
                f(com.franco.dev.graphql.operaciones.InventarioGraphQL.class, "finalizarInventario", "VER INVENTARIO", "CREAR INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioGraphQL.class, "cancelarInventario", "VER INVENTARIO", "CREAR INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioGraphQL.class, "reabrirInventario", "VER INVENTARIO", "CREAR INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioProductoGraphQL.class, "saveInventarioProducto", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioProductoGraphQL.class, "deleteInventarioProducto"),
                f(com.franco.dev.graphql.operaciones.InventarioProductoItemGraphQL.class, "saveInventarioProductoItem", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.InventarioProductoItemGraphQL.class, "deleteInventarioProductoItem", "VER INVENTARIO", "CREAR INVENTARIO", "PARTICIPAR DEL INVENTARIO"),
                f(com.franco.dev.graphql.operaciones.SesionInventarioGraphQL.class, "saveSesionInventario"),
                f(com.franco.dev.graphql.operaciones.SesionInventarioGraphQL.class, "deleteSesionInventario"),
                f(com.franco.dev.graphql.operaciones.EntradaGraphQL.class, "saveEntrada"),
                f(com.franco.dev.graphql.operaciones.EntradaGraphQL.class, "deleteEntrada"),
                f(com.franco.dev.graphql.operaciones.EntradaGraphQL.class, "finalizarEntrada"),
                f(com.franco.dev.graphql.operaciones.EntradaItemGraphQL.class, "saveEntradaItem"),
                f(com.franco.dev.graphql.operaciones.EntradaItemGraphQL.class, "deleteEntradaItem"),
                f(com.franco.dev.graphql.operaciones.SalidaGraphQL.class, "saveSalida"),
                f(com.franco.dev.graphql.operaciones.SalidaGraphQL.class, "deleteSalida"),
                f(com.franco.dev.graphql.operaciones.SalidaGraphQL.class, "finalizarSalida"),
                f(com.franco.dev.graphql.operaciones.SalidaItemGraphQL.class, "saveSalidaItem"),
                f(com.franco.dev.graphql.operaciones.SalidaItemGraphQL.class, "deleteSalidaItem"),
                // jornadas y horarios
                f(com.franco.dev.graphql.administrativo.JornadaGraphQL.class, "ajustarJornadaA8Horas", "RRHH GESTIONAR"),
                f(com.franco.dev.graphql.administrativo.JornadaGraphQL.class, "guardarObservacionJornada", "RRHH GESTIONAR"),
                f(com.franco.dev.graphql.administrativo.MarcacionGraphQL.class, "reprocesarJornadaDeMarcacion", "RRHH GESTIONAR"),
                f(com.franco.dev.graphql.administrativo.HorarioGraphQL.class, "saveHorario", "RRHH GESTIONAR", "EDITAR FUNCIONARIOS"),
                f(com.franco.dev.graphql.administrativo.HorarioGraphQL.class, "deleteHorario", "RRHH GESTIONAR", "EDITAR FUNCIONARIOS")
        );
    }

    static Object resolver(Fila fila, String... roles) {
        Object r = new ObjenesisStd().newInstance(fila.clase);
        // el falso se elige por el tipo del campo de seguridad del resolver
        Class<?> tipo = org.springframework.util.ReflectionUtils.findField(fila.clase, fila.campo).getType();
        Object seg = tipo == com.franco.dev.service.financiero.FacturacionSecurityService.class
                ? new FacturacionSecurityFalso(roles)
                : tipo == com.franco.dev.service.productos.PrecioSecurityService.class
                ? new com.franco.dev.service.productos.PrecioSecurityFalso(roles)
                : tipo == com.franco.dev.service.operaciones.compras.ComprasSecurityService.class
                ? new com.franco.dev.service.operaciones.compras.ComprasSecurityFalso(roles)
                : tipo == com.franco.dev.service.rrhh.RrhhSecurityService.class
                ? new com.franco.dev.service.rrhh.RrhhSecurityFalso(roles)
                : tipo == com.franco.dev.service.operaciones.InventarioSecurityService.class
                ? new com.franco.dev.service.operaciones.InventarioSecurityFalso(roles)
                : new TesoreriaSecurityFalso(1L, roles);
        ReflectionTestUtils.setField(r, fila.campo, seg);
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
