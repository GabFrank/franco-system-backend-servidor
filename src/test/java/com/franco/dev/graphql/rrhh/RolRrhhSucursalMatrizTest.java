package com.franco.dev.graphql.rrhh;

import com.franco.dev.graphql.financiero.RolCajaSoporte;
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
 * Qué puede llamar un usuario con solo RRHH SUCURSAL. El rol existe para que un gerente liquide sueldos y
 * finiquitos de sus sucursales sin ver el resto de RRHH: lo que no está en {@link #HABILITADOS} tiene que
 * rechazarlo.
 */
class RolRrhhSucursalMatrizTest {

    /** Los únicos resolvers que aceptan el rol acotado. Agregar uno acá es una decisión, no un descuido. */
    private static final Set<String> HABILITADOS = new TreeSet<>(Arrays.asList(
            "LiquidacionSueldoGraphQL.liquidacionSueldo",
            "LiquidacionSueldoGraphQL.funcionariosACargo",
            "LiquidacionSueldoGraphQL.imprimirReciboLiquidacion",
            "LiquidacionSueldoGraphQL.liquidacionesPorFuncionario",
            "LiquidacionSueldoGraphQL.liquidacionesPage",
            "LiquidacionSueldoGraphQL.liquidacionItems",
            "LiquidacionSueldoGraphQL.generarLiquidacionBorrador",
            "LiquidacionSueldoGraphQL.agregarItemLiquidacion",
            "LiquidacionSueldoGraphQL.eliminarItemLiquidacion",
            "LiquidacionSueldoGraphQL.editarItemLiquidacion",
            "LiquidacionSueldoGraphQL.generarLiquidacionesLote",
            "LiquidacionFinalGraphQL.liquidacionFinal",
            "LiquidacionFinalGraphQL.liquidacionesFinalesPorFuncionario",
            "LiquidacionFinalGraphQL.liquidacionFinalItems",
            "LiquidacionFinalGraphQL.previewLiquidacionFinal",
            "LiquidacionFinalGraphQL.generarLiquidacionFinal",
            "LiquidacionFinalGraphQL.agregarItemLiquidacionFinal",
            "LiquidacionFinalGraphQL.editarItemLiquidacionFinal",
            "LiquidacionFinalGraphQL.eliminarItemLiquidacionFinal",
            "LiquidacionConceptoGraphQL.liquidacionConceptosParaItemManual",
            "LiquidacionItemProgramadoGraphQL.itemsProgramadosPorFuncionario",
            "ReporteRrhhGraphQL.imprimirReciboFinal",
            "ReporteRrhhGraphQL.imprimirReciboItemLiquidacion",
            "ReporteRrhhGraphQL.imprimirReciboItemLiquidacionFinal",
            "PagoRrhhTesoreriaGraphQL.liquidacionesPendientesPago",
            "PagoRrhhTesoreriaGraphQL.finiquitosPendientesPago",
            "PagoRrhhTesoreriaGraphQL.pagarRrhhMixto"));

    private static final Pattern FIRMA = Pattern.compile("^    public [^=;]*?\\b(\\w+)\\(");
    private static final Pattern ACEPTA_ACOTADO = Pattern.compile("alcance\\.require|seg\\.SUCURSAL|RrhhSecurityService\\.SUCURSAL");

    private final RolRrhhSucursalSoporte s = new RolRrhhSucursalSoporte();

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
                "Un resolver acepta RRHH SUCURSAL sin estar en la lista (o al revés). Si es a propósito,"
                        + " confirmá que está delimitado por sucursal y agregalo a HABILITADOS.");
    }

    @Test
    void conSoloElRolAcotado_elRestoDeRrhhEsRechazado() {
        s.autenticar("gerente", "RRHH SUCURSAL");
        s.aCargoDe(12L);

        // Aprobar es de RRHH central; volver a borrador, anular y el pago por el atajo, también.
        rechaza(LiquidacionSueldoGraphQL.class, "aprobarLiquidacion", "volverBorradorLiquidacion",
                "pagarLiquidacion", "anularLiquidacion", "generarLiquidacionesMes", "liquidacionesPorPeriodo");
        rechaza(LiquidacionFinalGraphQL.class, "aprobarLiquidacionFinal", "volverBorradorLiquidacionFinal",
                "pagarLiquidacionFinal", "anularLiquidacionFinal");
        rechaza(PagoRrhhTesoreriaGraphQL.class, "aguinaldosPendientesPago");
        rechaza(ReporteRrhhGraphQL.class, "reporteNominaMes", "reporteResumenIps", "reporteValesPendientes",
                "reportePrestamosActivos", "reporteAguinaldoAnual", "imprimirReciboVale",
                "imprimirReciboPenalizacion", "imprimirActaAdvertencia", "imprimirReciboAguinaldo",
                "imprimirReciboPrestamo", "imprimirReciboBono");
        rechaza(DashboardRrhhGraphQL.class, "dashboardRrhhKpis", "nominaSeriePorMes", "rrhhTopExposicion",
                "rrhhTopHorasExtra", "rrhhFuncionariosIncompletos");
        rechaza(LiquidacionItemProgramadoGraphQL.class, "programarItemLiquidacion", "anularItemProgramado");
        rechaza(AguinaldoGraphQL.class, "calcularAguinaldosAnio");
        rechaza(BonoGraphQL.class, "bonosPage");
        rechaza(LiquidacionConceptoGraphQL.class, "saveLiquidacionConcepto");
    }

    @Test
    void conSoloElRolAcotado_loDelimitadoPorSucursalPasaElControlDeRol() {
        s.autenticar("gerente", "RRHH SUCURSAL");
        s.aCargoDe(12L);

        for (String habilitado : HABILITADOS) {
            String[] partes = habilitado.split("\\.");
            Throwable t = RolCajaSoporte.llamar(s.resolver(clase(partes[0])), partes[1]);
            // Con argumentos vacíos lo rechaza el alcance (no hay documento), no la falta de rol.
            assertFalse(t != null && t.getMessage() != null && t.getMessage().contains("se requiere"),
                    habilitado + ": " + t);
        }
    }

    @Test
    void sinNingunRolDeRrhh_loDelimitadoPorSucursalTambienEsRechazado() {
        s.autenticar("cajera", "TESORERIA CAJA");
        s.aCargoDe(12L);

        for (String habilitado : HABILITADOS) {
            // El recibo de sueldo tiene además el autoservicio del propio funcionario.
            if (habilitado.endsWith(".imprimirReciboLiquidacion")) continue;
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
            return Class.forName("com.franco.dev.graphql.rrhh." + nombre);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
