package com.franco.dev.graphql;

import com.franco.dev.graphql.ControlRolMatrizTest.Fila;
import com.franco.dev.graphql.productos.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.franco.dev.graphql.ControlRolMatrizTest.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matriz de las escrituras del catalogo de productos, con los mismos helpers que {@link ControlRolMatrizTest}. */
class ProductosControlRolMatrizTest {

    static final String EP = "EDITAR PRODUCTOS";

    static List<Fila> filas() {
        return Arrays.asList(
                f(ProductoGraphQL.class, "saveProducto", EP),
                f(ProductoGraphQL.class, "deleteProducto"),
                f(ProductoGraphQL.class, "saveImagenProducto"),
                f(CodigoGraphQL.class, "saveCodigo", EP),
                f(CodigoGraphQL.class, "deleteCodigo", EP),
                f(PresentacionGraphQL.class, "savePresentacion", EP),
                f(PresentacionGraphQL.class, "deletePresentacion", EP),
                f(PresentacionGraphQL.class, "saveImagenPresentacion", EP),
                f(PresentacionGraphQL.class, "generarImagenesMedianas"),
                f(FamiliaGraphQL.class, "saveFamilia", EP),
                f(FamiliaGraphQL.class, "deleteFamilia"),
                f(SubfamiliaGraphQL.class, "saveSubfamilia", EP),
                f(SubfamiliaGraphQL.class, "deleteSubfamilia"),
                f(ProductoProveedorGraphQL.class, "saveProductoProveedor", EP),
                f(ProductoProveedorGraphQL.class, "desvincularProductoProveedor", EP, "ANALISIS DE CAJA",
                        "ANALISIS FINANCIERO", "ANALISIS CONTABLE", "VER PRECIO COSTO", "RECIBIR PEDIDOS",
                        "TESORERIA VER", "TESORERIA GESTIONAR"),
                f(ComboGraphQL.class, "saveCombo"), f(ComboGraphQL.class, "deleteCombo"),
                f(ComboItemGraphQL.class, "saveComboItem"), f(ComboItemGraphQL.class, "deleteComboItem"),
                f(IngredienteGraphQL.class, "saveIngrediente"), f(IngredienteGraphQL.class, "deleteIngrediente"),
                f(ProductoIngredienteGraphQL.class, "saveProductoIngrediente"),
                f(ProductoIngredienteGraphQL.class, "deleteProductoIngrediente"),
                f(ProductoImagenGraphQL.class, "saveProductoImagen"),
                f(ProductoImagenGraphQL.class, "deleteProductoImagen"),
                f(ProductoPorSucursalGraphQL.class, "saveProductoPorSucursal"),
                f(ProductoPorSucursalGraphQL.class, "deleteProductoPorSucursal"));
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
