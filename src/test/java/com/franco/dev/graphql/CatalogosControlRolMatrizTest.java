package com.franco.dev.graphql;

import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.activos.FamiliaMuebleGraphQL;
import com.franco.dev.graphql.activos.MarcaGraphQL;
import com.franco.dev.graphql.activos.ModeloGraphQL;
import com.franco.dev.graphql.activos.TelemetriaGraphQL;
import com.franco.dev.graphql.activos.TipoCombustibleGraphQL;
import com.franco.dev.graphql.activos.TipoMuebleGraphQL;
import com.franco.dev.graphql.activos.TipoVehiculoGraphQL;
import com.franco.dev.graphql.empresarial.CargoGraphQL;
import com.franco.dev.graphql.empresarial.ImpresoraGraphQL;
import com.franco.dev.graphql.empresarial.PuntoDeVentaGraphQL;
import com.franco.dev.graphql.empresarial.SectorGraphQL;
import com.franco.dev.graphql.empresarial.resolver.ZonaGraphQL;
import com.franco.dev.graphql.equipos.MarcaEquipoGraphQL;
import com.franco.dev.graphql.equipos.ModeloEquipoGraphQL;
import com.franco.dev.graphql.equipos.TipoEquipoGraphQL;
import com.franco.dev.graphql.financiero.CajaCategoriaObservacionGraphQL;
import com.franco.dev.graphql.financiero.CajaMotivoObservacionGraphQL;
import com.franco.dev.graphql.financiero.CajaSubCategoriaObservacionGraphQL;
import com.franco.dev.graphql.financiero.FormatoQrPosGraphQL;
import com.franco.dev.graphql.financiero.TipoGastoGraphQL;
import com.franco.dev.graphql.general.BarrioGraphQL;
import com.franco.dev.graphql.general.CiudadGraphQL;
import com.franco.dev.graphql.general.PaisGraphQL;
import com.franco.dev.graphql.operaciones.CategoriaObservacionGraphQL;
import com.franco.dev.graphql.operaciones.MotivoAveriaGraphQL;
import com.franco.dev.graphql.operaciones.MotivoDiferenciaPedidoGraphQL;
import com.franco.dev.graphql.operaciones.MotivoObservacionGraphQL;
import com.franco.dev.graphql.operaciones.SubcategoriaObservacionGraphQL;
import com.franco.dev.graphql.productos.TipoPresentacionGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvCategoriaGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvGrupoGraphQL;
import com.franco.dev.graphql.productos.pdv.PdvGruposProductosGraphQL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.franco.dev.graphql.ControlRolMatrizTest.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogos de la lista BAJA, con los helpers de {@link ControlRolMatrizTest}. */
class CatalogosControlRolMatrizTest {

    static final String[] INV = com.franco.dev.service.operaciones.InventarioSecurityService.ROLES_INVENTARIO;

    static List<Fila> filas() {
        return Arrays.asList(
                f(FamiliaMuebleGraphQL.class, "saveFamiliaMueble"),
                f(FamiliaMuebleGraphQL.class, "deleteFamiliaMueble"),
                f(MarcaGraphQL.class, "saveMarca"),
                f(MarcaGraphQL.class, "deleteMarca"),
                f(ModeloGraphQL.class, "saveModelo"),
                f(ModeloGraphQL.class, "deleteModelo"),
                f(TelemetriaGraphQL.class, "saveTelemetria"),
                f(TelemetriaGraphQL.class, "deleteTelemetria"),
                f(TipoCombustibleGraphQL.class, "saveTipoCombustible"),
                f(TipoCombustibleGraphQL.class, "deleteTipoCombustible"),
                f(TipoMuebleGraphQL.class, "saveTipoMueble"),
                f(TipoMuebleGraphQL.class, "deleteTipoMueble"),
                f(TipoVehiculoGraphQL.class, "saveTipoVehiculo"),
                f(TipoVehiculoGraphQL.class, "deleteTipoVehiculo"),
                f(MarcaEquipoGraphQL.class, "saveMarcaEquipo"),
                f(MarcaEquipoGraphQL.class, "deleteMarcaEquipo"),
                f(ModeloEquipoGraphQL.class, "saveModeloEquipo"),
                f(ModeloEquipoGraphQL.class, "deleteModeloEquipo"),
                f(TipoEquipoGraphQL.class, "saveTipoEquipo"),
                f(TipoEquipoGraphQL.class, "deleteTipoEquipo"),
                f(BarrioGraphQL.class, "saveBarrio"),
                f(BarrioGraphQL.class, "deleteBarrio"),
                f(CiudadGraphQL.class, "saveCiudad"),
                f(CiudadGraphQL.class, "deleteCiudad"),
                f(PaisGraphQL.class, "savePais"),
                f(PaisGraphQL.class, "deletePais"),
                f(TipoPresentacionGraphQL.class, "saveTipoPresentacion"),
                f(TipoPresentacionGraphQL.class, "deleteTipoPresentacion"),
                f(PdvCategoriaGraphQL.class, "savePdvCategoria"),
                f(PdvCategoriaGraphQL.class, "deletePdvCategoria"),
                f(PdvGrupoGraphQL.class, "savePdvGrupo"),
                f(PdvGrupoGraphQL.class, "deletePdvGrupo"),
                f(PdvGruposProductosGraphQL.class, "savePdvGruposProductos"),
                f(PdvGruposProductosGraphQL.class, "deletePdvGruposProductos"),
                f(CargoGraphQL.class, "saveCargo", "RRHH GESTIONAR"),
                f(CargoGraphQL.class, "deleteCargo", "RRHH GESTIONAR"),
                f(ImpresoraGraphQL.class, "saveImpresora", "CONFIGURACION"),
                f(ImpresoraGraphQL.class, "deleteImpresora", "CONFIGURACION"),
                f(PuntoDeVentaGraphQL.class, "savePuntoDeVenta"),
                f(PuntoDeVentaGraphQL.class, "deletePuntoDeVenta"),
                f(SectorGraphQL.class, "saveSector", INV),
                f(SectorGraphQL.class, "deleteSector", INV),
                f(ZonaGraphQL.class, "saveZona", INV),
                f(ZonaGraphQL.class, "deleteZona", INV),
                f(CajaCategoriaObservacionGraphQL.class, "saveCajaCategoriaObservacion"),
                f(CajaCategoriaObservacionGraphQL.class, "deleteCajaCategoriaObservacion"),
                f(CajaMotivoObservacionGraphQL.class, "saveCajaMotivoObservacion"),
                f(CajaMotivoObservacionGraphQL.class, "deleteCajaMotivoObservacion"),
                f(CajaSubCategoriaObservacionGraphQL.class, "saveCajaSubCategoriaObservacion"),
                f(CajaSubCategoriaObservacionGraphQL.class, "deleteCajaSubCategoriaObservacion"),
                f(TipoGastoGraphQL.class, "saveTipoGasto", "ANALISIS DE CAJA"),
                f(TipoGastoGraphQL.class, "deleteTipoGasto", "ANALISIS DE CAJA"),
                f(FormatoQrPosGraphQL.class, "saveFormatoQrPos"),
                f(FormatoQrPosGraphQL.class, "desactivarFormatoQrPos"),
                f(CategoriaObservacionGraphQL.class, "saveCategoriaObservacion"),
                f(CategoriaObservacionGraphQL.class, "deleteCategoriaObservacion"),
                f(MotivoAveriaGraphQL.class, "saveMotivoAveria"),
                f(MotivoAveriaGraphQL.class, "deleteMotivoAveria"),
                f(MotivoDiferenciaPedidoGraphQL.class, "saveMotivoDiferenciaPedido"),
                f(MotivoDiferenciaPedidoGraphQL.class, "deleteMotivoDiferenciaPedido"),
                f(MotivoObservacionGraphQL.class, "saveMotivoObservacion"),
                f(MotivoObservacionGraphQL.class, "deleteMotivoObservacion"),
                f(SubcategoriaObservacionGraphQL.class, "saveSubcategoriaObservacion"),
                f(SubcategoriaObservacionGraphQL.class, "deleteSubcategoriaObservacion"));
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
}
