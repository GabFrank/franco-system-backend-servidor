package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GraficoDesgloseExcelSupport {

    private GraficoDesgloseExcelSupport() {
    }

    private static final Map<String, Integer> MESES_INDICE = construirIndiceMeses();
    private static final Pattern PATRON_ANHO = Pattern.compile("(\\d{4})");

    private static Map<String, Integer> construirIndiceMeses() {
        String[] nombres = {
                "enero", "febrero", "marzo", "abril", "mayo", "junio",
                "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        };
        String[] cortos = {
                "ene", "feb", "mar", "abr", "may", "jun",
                "jul", "ago", "sep", "oct", "nov", "dic"
        };
        Map<String, Integer> indice = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            indice.put(nombres[i], i + 1);
            indice.put(cortos[i], i + 1);
        }
        return indice;
    }

    static ColumnasExport recolectarColumnas(List<GraficoDesgloseFila> items) {
        Set<Integer> anhosSet = new LinkedHashSet<>();
        Set<String> periodosSet = new LinkedHashSet<>();

        for (GraficoDesgloseFila item : items) {
            for (DesgloseAnhoGrafico d : filtrarDesgloseAnhos(item)) {
                anhosSet.add(d.getAnio());
            }
            for (DesglosePeriodoGrafico d : filtrarDesglosePeriodos(item)) {
                periodosSet.add(d.getEtiqueta());
            }
        }

        ColumnasExport columnas = new ColumnasExport();
        columnas.anhos = anhosSet.stream().sorted().collect(Collectors.toList());
        columnas.periodos = ordenarPeriodos(periodosSet);
        columnas.mostrarTotalesAnho = columnas.anhos.size() > 1;
        return columnas;
    }

    /**
     * Ordena las etiquetas de período en orden cronológico: primero por año y
     * luego por mes. Así, al combinar filtros de varios años con meses, se
     * listan todos los meses de un año antes de pasar al siguiente
     * (ej.: Enero 2024, …, Diciembre 2024, Enero 2025, …).
     */
    static List<String> ordenarPeriodos(Set<String> periodos) {
        return periodos.stream()
                .sorted(Comparator
                        .comparingInt(GraficoDesgloseExcelSupport::anhoDePeriodo)
                        .thenComparingInt(GraficoDesgloseExcelSupport::mesDePeriodo)
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
    }

    private static int anhoDePeriodo(String etiqueta) {
        if (etiqueta == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = PATRON_ANHO.matcher(etiqueta);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private static int mesDePeriodo(String etiqueta) {
        if (etiqueta == null) {
            return Integer.MAX_VALUE;
        }
        String normalizado = etiqueta.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> entry : MESES_INDICE.entrySet()) {
            if (normalizado.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Integer.MAX_VALUE;
    }

    static List<String> construirEncabezadosGrafico(String columnaEtiqueta, ColumnasExport columnas) {
        List<String> headers = new ArrayList<>();
        headers.add(columnaEtiqueta);
        if (columnas.mostrarTotalesAnho) {
            for (Integer anio : columnas.anhos) {
                headers.add(String.valueOf(anio));
            }
            headers.add("Total combinado");
        } else {
            headers.add("Total");
        }
        headers.add("Cant. ventas");
        return headers;
    }

    /**
     * La tabla de detalle comparativa solo aporta valor cuando hay realmente
     * algo que comparar: más de un año o más de un período (ej.: varios meses).
     * Con un único año y un único mes, repetiría la información de la primera
     * tabla, por lo que se omite.
     */
    static boolean requiereTablaComparativa(ColumnasExport columnas) {
        return columnas.mostrarTotalesAnho || columnas.periodos.size() > 1;
    }

    static List<String> construirEncabezadosDetalle(String columnaEtiqueta, ColumnasExport columnas) {
        List<String> headers = new ArrayList<>();
        headers.add(columnaEtiqueta);
        headers.add("Total combinado");
        headers.add("Cant. ventas");
        if (columnas.mostrarTotalesAnho) {
            for (Integer anio : columnas.anhos) {
                headers.add("Total " + anio);
                headers.add("Ventas " + anio);
            }
        }
        if (columnas.periodos.size() > 1) {
            for (String etiqueta : columnas.periodos) {
                headers.add(etiqueta + " - Monto");
                headers.add(etiqueta + " - Ventas");
            }
        }
        return headers;
    }

    static void escribirFilaDetalle(
            Row row,
            GraficoDesgloseFila item,
            ColumnasExport columnas,
            CellStyle textoStyle,
            CellStyle monedaStyle,
            CellStyle enteroStyle,
            int colInicio
    ) {
        int col = colInicio;
        Cell etiquetaCell = row.createCell(col++);
        etiquetaCell.setCellValue(item.getEtiqueta());
        etiquetaCell.setCellStyle(textoStyle);

        Cell totalCell = row.createCell(col++);
        totalCell.setCellValue(item.getTotal());
        totalCell.setCellStyle(monedaStyle);

        Cell cantTotalCell = row.createCell(col++);
        cantTotalCell.setCellValue(item.getCantidadVentas());
        cantTotalCell.setCellStyle(enteroStyle);

        if (columnas.mostrarTotalesAnho) {
            Map<Integer, DesgloseAnhoGrafico> mapaAnho = filtrarDesgloseAnhos(item).stream()
                    .collect(Collectors.toMap(DesgloseAnhoGrafico::getAnio, d -> d, (a, b) -> a));
            for (Integer anio : columnas.anhos) {
                DesgloseAnhoGrafico d = mapaAnho.get(anio);
                Cell monto = row.createCell(col++);
                monto.setCellValue(d != null && d.getTotal() != null ? d.getTotal() : 0);
                monto.setCellStyle(monedaStyle);
                Cell cant = row.createCell(col++);
                cant.setCellValue(d != null && d.getCantidad() != null ? d.getCantidad() : 0);
                cant.setCellStyle(enteroStyle);
            }
        }

        if (columnas.periodos.size() > 1) {
            Map<String, DesglosePeriodoGrafico> mapaPeriodo = filtrarDesglosePeriodos(item).stream()
                    .collect(Collectors.toMap(DesglosePeriodoGrafico::getEtiqueta, d -> d, (a, b) -> a));
            for (String etiqueta : columnas.periodos) {
                DesglosePeriodoGrafico d = mapaPeriodo.get(etiqueta);
                Cell monto = row.createCell(col++);
                monto.setCellValue(d != null && d.getTotal() != null ? d.getTotal() : 0);
                monto.setCellStyle(monedaStyle);
                Cell cant = row.createCell(col++);
                cant.setCellValue(d != null && d.getCantidad() != null ? d.getCantidad() : 0);
                cant.setCellStyle(enteroStyle);
            }
        }
    }

    /**
     * Escribe la fila de totales (sumatoria) al pie de la tabla de detalle,
     * respetando exactamente el mismo orden de columnas que
     * {@link #construirEncabezadosDetalle}.
     */
    static void escribirFilaTotalDetalle(
            Row row,
            List<GraficoDesgloseFila> validas,
            ColumnasExport columnas,
            CellStyle totalTextoStyle,
            CellStyle totalMonedaStyle,
            CellStyle totalEnteroStyle,
            int colInicio
    ) {
        int col = colInicio;
        Cell etiquetaCell = row.createCell(col++);
        etiquetaCell.setCellValue("Total");
        etiquetaCell.setCellStyle(totalTextoStyle);

        crearCeldaTotal(row, col++, validas.stream()
                .mapToDouble(GraficoDesgloseFila::getTotal).sum(), totalMonedaStyle);
        crearCeldaTotal(row, col++, validas.stream()
                .mapToDouble(GraficoDesgloseFila::getCantidadVentas).sum(), totalEnteroStyle);

        if (columnas.mostrarTotalesAnho) {
            for (Integer anio : columnas.anhos) {
                double sumaMonto = 0;
                double sumaCant = 0;
                for (GraficoDesgloseFila item : validas) {
                    DesgloseAnhoGrafico d = filtrarDesgloseAnhos(item).stream()
                            .filter(x -> anio.equals(x.getAnio()))
                            .findFirst()
                            .orElse(null);
                    if (d != null) {
                        sumaMonto += d.getTotal() != null ? d.getTotal() : 0;
                        sumaCant += d.getCantidad() != null ? d.getCantidad() : 0;
                    }
                }
                crearCeldaTotal(row, col++, sumaMonto, totalMonedaStyle);
                crearCeldaTotal(row, col++, sumaCant, totalEnteroStyle);
            }
        }

        if (columnas.periodos.size() > 1) {
            for (String etiqueta : columnas.periodos) {
                double sumaMonto = 0;
                double sumaCant = 0;
                for (GraficoDesgloseFila item : validas) {
                    DesglosePeriodoGrafico d = filtrarDesglosePeriodos(item).stream()
                            .filter(x -> etiqueta.equals(x.getEtiqueta()))
                            .findFirst()
                            .orElse(null);
                    if (d != null) {
                        sumaMonto += d.getTotal() != null ? d.getTotal() : 0;
                        sumaCant += d.getCantidad() != null ? d.getCantidad() : 0;
                    }
                }
                crearCeldaTotal(row, col++, sumaMonto, totalMonedaStyle);
                crearCeldaTotal(row, col++, sumaCant, totalEnteroStyle);
            }
        }
    }

    private static void crearCeldaTotal(Row row, int col, double valor, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(style);
    }

    private static List<DesgloseAnhoGrafico> filtrarDesgloseAnhos(GraficoDesgloseFila item) {
        return item.getDesgloseAnhos().stream()
                .filter(d -> (d.getTotal() != null && d.getTotal() != 0)
                        || (d.getCantidad() != null && d.getCantidad() > 0))
                .sorted(Comparator.comparing(DesgloseAnhoGrafico::getAnio))
                .collect(Collectors.toList());
    }

    private static List<DesglosePeriodoGrafico> filtrarDesglosePeriodos(GraficoDesgloseFila item) {
        return item.getDesglosePeriodos().stream()
                .filter(d -> (d.getTotal() != null && d.getTotal() != 0)
                        || (d.getCantidad() != null && d.getCantidad() > 0))
                .sorted(Comparator.comparing(DesglosePeriodoGrafico::getEtiqueta))
                .collect(Collectors.toList());
    }

    static class ColumnasExport {
        List<Integer> anhos = List.of();
        List<String> periodos = List.of();
        boolean mostrarTotalesAnho;
    }
}
