package com.franco.dev.graphql;

import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.operaciones.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.franco.dev.graphql.ControlRolMatrizTest.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matriz de compras y recepcion, con los mismos helpers que {@link ControlRolMatrizTest}. */
class ComprasControlRolMatrizTest {

    static final String[] COMPRAS = {"ANALISIS DE CAJA", "ANALISIS FINANCIERO", "ANALISIS CONTABLE", "VER PRECIO COSTO", "RECIBIR PEDIDOS", "TESORERIA VER", "TESORERIA GESTIONAR"};

    static List<Fila> filas() {
        return Arrays.asList(
                f(GuardadoPedidoItemGraphQL.class, "guardarPedidoItemConDistribuciones", COMPRAS),
                f(NecesidadGraphQL.class, "saveNecesidad"),
                f(NecesidadGraphQL.class, "deleteNecesidad"),
                f(NecesidadItemGraphQL.class, "saveNecesidadItem"),
                f(NecesidadItemGraphQL.class, "deleteNecesidadItem"),
                f(NotaPedidoGraphQL.class, "saveNotaPedido"),
                f(NotaPedidoGraphQL.class, "deleteNotaPedido"),
                f(NotaRecepcionGraphQL.class, "saveNotaRecepcion", COMPRAS),
                f(NotaRecepcionGraphQL.class, "deleteNotaRecepcion", COMPRAS),
                f(NotaRecepcionGraphQL.class, "asignarItemsANota", COMPRAS),
                f(NotaRecepcionItemDistribucionGraphQL.class, "saveNotaRecepcionItemDistribucion"),
                f(NotaRecepcionItemDistribucionGraphQL.class, "saveNotaRecepcionItemDistribuciones"),
                f(NotaRecepcionItemDistribucionGraphQL.class, "replaceNotaRecepcionItemDistribuciones", COMPRAS),
                f(NotaRecepcionItemDistribucionGraphQL.class, "deleteNotaRecepcionItemDistribucion"),
                f(NotaRecepcionItemDistribucionGraphQL.class, "deleteNotaRecepcionItemDistribucionesByNotaRecepcionItemId"),
                f(NotaRecepcionItemGraphQL.class, "saveNotaRecepcionItem", COMPRAS),
                f(NotaRecepcionItemGraphQL.class, "deleteNotaRecepcionItem", COMPRAS),
                f(NotaRecepcionItemGraphQL.class, "adicionarItens"),
                f(NotaRecepcionItemGraphQL.class, "removerItens"),
                f(PedidoFechaEntregaGraphQL.class, "savePedidoFechaEntrega"),
                f(PedidoFechaEntregaGraphQL.class, "deletePedidoFechaEntrega"),
                f(PedidoGraphQL.class, "deletePedido", COMPRAS),
                f(PedidoGraphQL.class, "savePedido"),
                f(PedidoGraphQL.class, "savePedidoFull", COMPRAS),
                f(PedidoGraphQL.class, "finalizarCreacionPedido", COMPRAS),
                f(PedidoGraphQL.class, "finalizarRecepcionNotas", COMPRAS),
                f(PedidoItemDistribucionGraphQL.class, "savePedidoItemDistribucion"),
                f(PedidoItemDistribucionGraphQL.class, "deletePedidoItemDistribucion"),
                f(PedidoItemDistribucionGraphQL.class, "savePedidoItemDistribuciones"),
                f(PedidoItemDistribucionGraphQL.class, "replacePedidoItemDistribuciones"),
                f(PedidoItemDistribucionGraphQL.class, "deletePedidoItemDistribucionesByPedidoItemId"),
                f(PedidoItemDistribucionGraphQL.class, "deletePedidoItemDistribucionesByIds"),
                f(PedidoItemDistribucionGraphQL.class, "mergePedidoItemDistribuciones", COMPRAS),
                f(PedidoItemGraphQL.class, "deletePedidoItem", COMPRAS),
                f(PedidoItemGraphQL.class, "savePedidoItem", COMPRAS),
                f(PedidoItemGraphQL.class, "updateNotaRecepcion"),
                f(PedidoItemGraphQL.class, "addPedidoItemToNotaRecepcion"),
                f(PedidoItemGraphQL.class, "verificarRecepcionProducto"),
                f(PedidoSucursalEntregaGraphQL.class, "savePedidoSucursalEntrega"),
                f(PedidoSucursalEntregaGraphQL.class, "deletePedidoSucursalEntrega"),
                f(PedidoSucursalInfluenciaGraphQL.class, "savePedidoSucursalInfluencia"),
                f(PedidoSucursalInfluenciaGraphQL.class, "deletePedidoSucursalInfluencia"),
                f(ProcesoEtapaGraphQL.class, "saveProcesoEtapa"),
                f(ProcesoEtapaGraphQL.class, "iniciarEtapa"),
                f(ProcesoEtapaGraphQL.class, "finalizarEtapa"),
                f(ProcesoEtapaGraphQL.class, "omitirEtapa"),
                f(ProcesoEtapaGraphQL.class, "revertirEtapaCreacion", COMPRAS),
                f(RecepcionCostoAdicionalGraphQL.class, "saveRecepcionCostoAdicional"),
                f(RecepcionCostoAdicionalGraphQL.class, "deleteRecepcionCostoAdicional"),
                f(RecepcionDocumentalGraphQL.class, "rechazarNotaRecepcionItem", COMPRAS),
                f(RecepcionDocumentalGraphQL.class, "dividirPedidoItemEnNotas", COMPRAS),
                f(RecepcionMercaderiaGraphQL.class, "saveRecepcionMercaderia"),
                f(RecepcionMercaderiaGraphQL.class, "finalizarRecepcionMercaderia", COMPRAS),
                f(RecepcionMercaderiaGraphQL.class, "iniciarRecepcion", COMPRAS),
                f(RecepcionMercaderiaGraphQL.class, "crearRecepcionMercaderia", COMPRAS),
                f(RecepcionMercaderiaGraphQL.class, "asociarNotasARecepcion", COMPRAS),
                f(RecepcionMercaderiaGraphQL.class, "reabrirRecepcionMercaderia", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "saveRecepcionMercaderiaItem", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "cancelarVerificacion", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "resetearVerificacion"),
                f(RecepcionMercaderiaItemGraphQL.class, "deshacerVerificacionPorProducto", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "verificarProductoMobile", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "cancelarRechazo", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "rechazarItem", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "recepcionarTodoPorNota", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "deshacerVerificacionTodoPorNota", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "validarFinalizacionRecepcionPorPedido", COMPRAS),
                f(RecepcionMercaderiaItemGraphQL.class, "finalizarRecepcionFisicaPorPedido", COMPRAS),
                f(RecepcionMercaderiaItemVariacionGraphQL.class, "deleteRecepcionMercaderiaItemVariacion"),
                f(RecepcionMercaderiaNotaGraphQL.class, "asociarNotaARecepcion"),
                f(RecepcionMercaderiaNotaGraphQL.class, "desasociarNotaDeRecepcion"),
                f(RecepcionMercaderiaNotaGraphQL.class, "deleteRecepcionMercaderiaNota"));
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

    /** invocar() toma la primera sobrecarga: las dos de savePedidoItemDistribuciones se prueban aparte. */
    @org.junit.jupiter.api.Test
    void lasDosSobrecargasDeSavePedidoItemDistribucionesSonSoloAdmin() throws Exception {
        Fila fila = f(PedidoItemDistribucionGraphQL.class, "savePedidoItemDistribuciones");
        int vistas = 0;
        for (java.lang.reflect.Method m : PedidoItemDistribucionGraphQL.class.getMethods()) {
            if (!m.getName().equals(fila.metodo)) continue;
            vistas++;
            for (String[] roles : new String[][]{{}, COMPRAS}) {
                try {
                    m.invoke(resolver(fila, roles), new Object[m.getParameterCount()]);
                    assertTrue(false, m + " no se rechazo");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    assertTrue(esRechazo(e.getCause()), m + ": " + e.getCause());
                }
            }
        }
        assertTrue(vistas == 2, "sobrecargas: " + vistas);
    }
}
