package com.franco.dev.graphql.financiero;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Qué puede llamar un usuario con solo TESORERIA CAJA. El rol existe para que un gerente de sucursal
 * opere sus cajas sin ver nada global: lo que no está en {@link #HABILITADOS} tiene que rechazarlo.
 */
class RolTesoreriaCajaMatrizTest {

    /** Los únicos resolvers que aceptan el rol acotado. Agregar uno acá es una decisión, no un descuido. */
    private static final Set<String> HABILITADOS = new TreeSet<>(Arrays.asList(
            "CajaVirtualGraphQL.cajaVirtual",
            "CajaVirtualGraphQL.cajaVirtuales",
            "CajaVirtualGraphQL.cajaVirtualesFilter",
            "CajaVirtualGraphQL.cajaVirtualSaldos",
            "CajaVirtualGraphQL.cajaVirtualesPorTipo",
            "CajaVirtualGraphQL.cajaVirtualesPorSucursal",
            "CajaVirtualGraphQL.cajaVirtualesActivas",
            "MovimientoCajaVirtualGraphQL.movimientoCajaVirtual",
            "MovimientoCajaVirtualGraphQL.movimientosCajaVirtual",
            "MovimientoCajaVirtualGraphQL.movimientosCajaVirtualPorFecha",
            "MovimientoCajaVirtualGraphQL.movimientosCajaVirtualFilter",
            "MovimientoCajaVirtualGraphQL.anularMovimientoCajaVirtual",
            "MovimientoCajaVirtualGraphQL.registrarMovimientosCajaVirtual",
            "MovimientoCajaVirtualGraphQL.realizarTransferenciasCajaVirtual",
            "MovimientoCajaVirtualGraphQL.realizarTransferenciaCajaVirtual",
            "ReporteMovimientosCajaGraphQL.imprimirReporteMovimientosCajaVirtual",
            "EntradaVariaGraphQL.entradaVariaCategorias",
            "EntradaVariaGraphQL.entradasVarias",
            "EntradaVariaGraphQL.registrarEntradaVaria",
            "EntradaVariaGraphQL.anularEntradaVaria",
            "OperacionFinancieraGraphQL.operacionFinancieraCategorias",
            "RetiroGraphQL.retirosFlotantes",
            "RetiroGraphQL.ingresarRetiroACajaMayor",
            "CajaVirtualGraphQL.cuentasBancariasParaDeposito",
            "OperacionFinancieraGraphQL.registrarOperacionFinanciera",
            "OperacionFinancieraGraphQL.anularOperacionFinanciera",
            "PagoProveedorGraphQL.solicitudesPagoPendientes",
            "PagoProveedorGraphQL.gastosPendientes",
            "PagoProveedorGraphQL.detalleDePago",
            "PagoProveedorGraphQL.crearGastoParaPago",
            "PagoProveedorGraphQL.pagarSolicitudesMixto",
            "PagoProveedorGraphQL.anularPagoCpp"));

    private static final Pattern FIRMA = Pattern.compile("^    public [^=;]*?\\b(\\w+)\\(");
    private static final Pattern ACEPTA_ACOTADO = Pattern.compile(
            "requireVerCaja\\(|requireOperarCaja\\(|requirePagarCppCaja\\(|TesoreriaSecurityService\\.CAJA");

    private final RolCajaSoporte s = new RolCajaSoporte();

    @AfterEach
    void tearDown() {
        RolCajaSoporte.limpiar();
    }

    @Test
    void laListaDeResolversQueAceptanElRolAcotado_esCerrada() throws IOException {
        Set<String> encontrados = new TreeSet<>();
        Path raiz = Paths.get("src/main/java/com/franco/dev/graphql");
        List<Path> fuentes;
        try (Stream<Path> w = Files.walk(raiz)) {
            fuentes = w.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        for (Path fuente : fuentes) {
            String clase = fuente.getFileName().toString().replace(".java", "");
            String metodo = null;
            for (String linea : Files.readAllLines(fuente)) {
                Matcher f = FIRMA.matcher(linea);
                if (f.find()) metodo = f.group(1);
                if (metodo != null && ACEPTA_ACOTADO.matcher(linea).find()) encontrados.add(clase + "." + metodo);
            }
        }
        assertEquals(HABILITADOS, encontrados,
                "Un resolver acepta TESORERIA CAJA sin estar en la lista (o al revés). Si es a propósito,"
                        + " confirmá que está delimitado por caja y agregalo a HABILITADOS.");
    }

    @Test
    void conSoloElRolAcotado_loGlobalDeTesoreriaEsRechazado() {
        s.autenticar("gerente", "TESORERIA CAJA");

        rechaza(TesoreriaReporteGraphQL.class, "saldoConsolidadoTesoreria", "proximosVencimientosTesoreria",
                "agingCppTesoreria");
        rechaza(OperacionFinancieraGraphQL.class, "operacionesFinancieras", "operacionFinanciera",
                "movimientosBancarios");
        rechaza(CuentaBancariaGraphQL.class, "cuentaBancaria", "cuentasBancarias", "cuentasBancariasOperables",
                "cuentaBancariasSearch", "countCuentaBancaria", "saveCuentaBancaria", "deleteCuentaBancaria",
                "ajustarSaldoCuentaBancaria");
        rechaza(ChequeGraphQL.class, "cheque", "cheques", "chequesSearch");
        rechaza(ChequeDashboardGraphQL.class, "chequesDashboard", "chequesResumenPorDia", "chequesSaldosPorChequera");
        rechaza(CajaVirtualGraphQL.class, "cajaVirtualResumenBancario", "saveCajaVirtual", "deleteCajaVirtual");
        rechaza(CajaVirtualConfiguracionGraphQL.class, "cajaVirtualConfiguracion", "saveCajaVirtualConfiguracion");
        rechaza(MovimientoCajaVirtualGraphQL.class, "saveMovimientoCajaVirtual");
        rechaza(ReporteMovimientosCajaGraphQL.class, "imprimirReporteMovimientosBancarios");
        rechaza(EntradaVariaGraphQL.class, "saveEntradaVariaCategoria");
        rechaza(AjusteDeSaldoGraphQL.class, "ajustarCajaVirtualPorConteo");
        rechaza(PagoProveedorGraphQL.class, "pagarSolicitudesLoteCajaMayor", "pagarSolicitud",
                "asignarSolicitudPagoACaja");
        rechaza(com.franco.dev.graphql.operaciones.SolicitudPagoGraphQL.class, "devolverSolicitudPago");
        rechaza(RetiroGraphQL.class, "cancelarRetiro");
        rechaza(RetiroVerificacionGraphQL.class, "verificacionDeRetiro", "retiroCasos", "retiroCasosAbiertos",
                "verificarRetiro", "anularVerificacionRetiro");
    }

    @Test
    void conSoloElRolAcotado_loDelimitadoPorCajaPasaElControlDeRol() {
        s.autenticar("gerente", "TESORERIA CAJA");

        for (String habilitado : HABILITADOS) {
            String[] partes = habilitado.split("\\.");
            Throwable t = RolCajaSoporte.llamar(s.resolver(clase(partes[0])), partes[1]);
            assertFalse(RolCajaSoporte.esNoAutorizado(t), habilitado + ": " + t);
        }
    }

    @Test
    void sinRolDeTesoreria_loDelimitadoPorCajaTambienEsRechazado() {
        s.autenticar("cajera", "ANALISIS DE CAJA");

        for (String habilitado : HABILITADOS) {
            if (habilitado.equals("CajaVirtualGraphQL.cajaVirtualesActivas")) continue;   // compartida con RRHH
            String[] partes = habilitado.split("\\.");
            Throwable t = RolCajaSoporte.llamar(s.resolver(clase(partes[0])), partes[1]);
            assertTrue(RolCajaSoporte.esNoAutorizado(t), habilitado + ": " + t);
        }
    }

    private void rechaza(Class<?> tipo, String... metodos) {
        Object resolver = s.resolver(tipo);
        for (String metodo : metodos) {
            Throwable t = RolCajaSoporte.llamar(resolver, metodo);
            assertTrue(RolCajaSoporte.esNoAutorizado(t), tipo.getSimpleName() + "." + metodo + ": " + t);
        }
    }

    private static Class<?> clase(String nombre) {
        try {
            return Class.forName("com.franco.dev.graphql.financiero." + nombre);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
