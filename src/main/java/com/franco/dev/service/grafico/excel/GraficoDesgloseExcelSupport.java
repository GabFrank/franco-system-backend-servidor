package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.grafico.DesgloseAnhoGrafico;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class GraficoDesgloseExcelSupport {

    private GraficoDesgloseExcelSupport() {
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
        columnas.periodos = periodosSet.stream().sorted().collect(Collectors.toList());
        columnas.mostrarTotalesAnho = columnas.anhos.size() > 1;
        return columnas;
    }

    static List<String> construirEncabezadosGrafico(String columnaEtiqueta, ColumnasExport columnas) {
        List<String> headers = new ArrayList<>();
        headers.add(columnaEtiqueta);
        if (columnas.mostrarTotalesAnho) {
            for (Integer anio : columnas.anhos) {
                headers.add(String.valueOf(anio));
            }
        } else {
            headers.add("Total");
        }
        return headers;
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
            CellStyle monedaStyle,
            CellStyle enteroStyle,
            int colInicio
    ) {
        int col = colInicio;
        row.createCell(col++).setCellValue(item.getEtiqueta());

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
