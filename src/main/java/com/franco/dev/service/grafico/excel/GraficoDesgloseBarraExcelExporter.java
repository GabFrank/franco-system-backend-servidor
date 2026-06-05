package com.franco.dev.service.grafico.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.ColumnasExport;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.construirEncabezadosDetalle;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.construirEncabezadosGrafico;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.escribirFilaDetalle;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.escribirFilaTotalDetalle;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.recolectarColumnas;
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.requiereTablaComparativa;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.COLUMNAS_SEPARACION_TABLAS;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.ajustarAnchoColumna;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloEncabezado;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloEntero;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloMoneda;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloTexto;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloTotalEntero;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloTotalMoneda;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloTotalTexto;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearGraficoSegunFormato;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.escribirMetadatos;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.escribirWorkbook;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.filaInicioTablas;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.obtenerOCrearFila;

@Component
public class GraficoDesgloseBarraExcelExporter {

    public byte[] exportar(
            GraficoExcelFiltrosContext filtros,
            String nombreHoja,
            String columnaEtiqueta,
            String etiquetaDetalle,
            String tituloGrafico,
            List<GraficoDesgloseFila> filas,
            GraficoExcelFormatoVisual formatoGrafico
    ) throws IOException {
        List<GraficoDesgloseFila> validas = filas.stream()
                .filter(f -> f.getTotal() > 0)
                .sorted(Comparator.comparing(GraficoDesgloseFila::getTotal).reversed())
                .collect(Collectors.toList());

        if (validas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }

        ColumnasExport columnas = recolectarColumnas(validas);
        boolean graficoSoloTotal = formatoGrafico == GraficoExcelFormatoVisual.TORTA;
        boolean mostrarDetalle = requiereTablaComparativa(columnas);
        int filaEncabezadoGrafico = filaInicioTablas();
        int filaInicioDatosGrafico = filaEncabezadoGrafico + 1;
        int filaFinDatosGrafico = filaInicioDatosGrafico + validas.size() - 1;
        int colInicioDetalle = (graficoSoloTotal ? 3 : construirEncabezadosGrafico(columnaEtiqueta, columnas).size())
                + COLUMNAS_SEPARACION_TABLAS;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(nombreHoja);
            CellStyle headerStyle = crearEstiloEncabezado(workbook);
            CellStyle textoStyle = crearEstiloTexto(workbook);
            CellStyle monedaStyle = crearEstiloMoneda(workbook);
            CellStyle enteroStyle = crearEstiloEntero(workbook);
            CellStyle totalTextoStyle = crearEstiloTotalTexto(workbook);
            CellStyle totalMonedaStyle = crearEstiloTotalMoneda(workbook);
            CellStyle totalEnteroStyle = crearEstiloTotalEntero(workbook);

            escribirMetadatos(sheet, filtros);
            escribirTablaGrafico(
                    sheet,
                    validas,
                    columnas,
                    columnaEtiqueta,
                    filaEncabezadoGrafico,
                    filaInicioDatosGrafico,
                    headerStyle,
                    textoStyle,
                    monedaStyle,
                    enteroStyle,
                    totalTextoStyle,
                    totalMonedaStyle,
                    totalEnteroStyle,
                    graficoSoloTotal
            );

            int colFinSeries = graficoSoloTotal
                    ? 1
                    : (columnas.mostrarTotalesAnho ? columnas.anhos.size() : 1);
            crearGraficoSegunFormato(
                    formatoGrafico,
                    sheet,
                    tituloGrafico,
                    filaEncabezadoGrafico,
                    filaInicioDatosGrafico,
                    filaFinDatosGrafico,
                    1,
                    colFinSeries
            );

            if (mostrarDetalle) {
                escribirTablaDetalle(
                        sheet,
                        validas,
                        columnas,
                        columnaEtiqueta,
                        etiquetaDetalle,
                        colInicioDetalle,
                        filaEncabezadoGrafico - 1,
                        filaEncabezadoGrafico,
                        filaInicioDatosGrafico,
                        headerStyle,
                        textoStyle,
                        monedaStyle,
                        enteroStyle,
                        totalTextoStyle,
                        totalMonedaStyle,
                        totalEnteroStyle
                );
            }

            ajustarAnchos(sheet, columnas, columnaEtiqueta, colInicioDetalle, graficoSoloTotal, mostrarDetalle);
            return escribirWorkbook(workbook);
        }
    }

    private void escribirTablaGrafico(
            XSSFSheet sheet,
            List<GraficoDesgloseFila> validas,
            ColumnasExport columnas,
            String columnaEtiqueta,
            int filaEncabezado,
            int filaInicioDatos,
            CellStyle headerStyle,
            CellStyle textoStyle,
            CellStyle monedaStyle,
            CellStyle enteroStyle,
            CellStyle totalTextoStyle,
            CellStyle totalMonedaStyle,
            CellStyle totalEnteroStyle,
            boolean soloTotal
    ) {
        Row header = sheet.createRow(filaEncabezado);
        List<String> headersGrafico = soloTotal
                ? List.of(columnaEtiqueta, "Total", "Cant. ventas")
                : construirEncabezadosGrafico(columnaEtiqueta, columnas);
        for (int c = 0; c < headersGrafico.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headersGrafico.get(c));
            cell.setCellStyle(headerStyle);
        }

        boolean totalesPorAnho = !soloTotal && columnas.mostrarTotalesAnho;
        int fila = filaInicioDatos;
        for (GraficoDesgloseFila item : validas) {
            Row row = sheet.createRow(fila++);
            int col = 0;
            Cell etiquetaCell = row.createCell(col++);
            etiquetaCell.setCellValue(item.getEtiqueta());
            etiquetaCell.setCellStyle(textoStyle);
            if (totalesPorAnho) {
                Map<Integer, com.franco.dev.domain.grafico.DesgloseAnhoGrafico> mapa =
                        item.getDesgloseAnhos().stream()
                                .collect(Collectors.toMap(
                                        com.franco.dev.domain.grafico.DesgloseAnhoGrafico::getAnio,
                                        d -> d,
                                        (a, b) -> a
                                ));
                for (Integer anio : columnas.anhos) {
                    var d = mapa.get(anio);
                    Cell cell = row.createCell(col++);
                    cell.setCellValue(d != null && d.getTotal() != null ? d.getTotal() : 0);
                    cell.setCellStyle(monedaStyle);
                }
                Cell totalCell = row.createCell(col++);
                totalCell.setCellValue(item.getTotal());
                totalCell.setCellStyle(monedaStyle);
            } else {
                Cell cell = row.createCell(col++);
                cell.setCellValue(item.getTotal());
                cell.setCellStyle(monedaStyle);
            }
            Cell cantCell = row.createCell(col);
            cantCell.setCellValue(item.getCantidadVentas());
            cantCell.setCellStyle(enteroStyle);
        }

        escribirFilaTotalGrafico(
                sheet,
                validas,
                columnas,
                fila,
                totalesPorAnho,
                totalTextoStyle,
                totalMonedaStyle,
                totalEnteroStyle
        );
    }

    private void escribirFilaTotalGrafico(
            XSSFSheet sheet,
            List<GraficoDesgloseFila> validas,
            ColumnasExport columnas,
            int filaTotal,
            boolean totalesPorAnho,
            CellStyle totalTextoStyle,
            CellStyle totalMonedaStyle,
            CellStyle totalEnteroStyle
    ) {
        Row row = obtenerOCrearFila(sheet, filaTotal);
        int col = 0;
        Cell etiqueta = row.createCell(col++);
        etiqueta.setCellValue("Total");
        etiqueta.setCellStyle(totalTextoStyle);

        if (totalesPorAnho) {
            for (Integer anio : columnas.anhos) {
                double suma = validas.stream()
                        .mapToDouble(item -> item.getDesgloseAnhos().stream()
                                .filter(d -> anio.equals(d.getAnio()) && d.getTotal() != null)
                                .mapToDouble(com.franco.dev.domain.grafico.DesgloseAnhoGrafico::getTotal)
                                .sum())
                        .sum();
                Cell cell = row.createCell(col++);
                cell.setCellValue(suma);
                cell.setCellStyle(totalMonedaStyle);
            }
        }

        double sumaTotal = validas.stream()
                .mapToDouble(GraficoDesgloseFila::getTotal)
                .sum();
        Cell totalCell = row.createCell(col++);
        totalCell.setCellValue(sumaTotal);
        totalCell.setCellStyle(totalMonedaStyle);

        double sumaCantidad = validas.stream()
                .mapToDouble(GraficoDesgloseFila::getCantidadVentas)
                .sum();
        Cell cantCell = row.createCell(col);
        cantCell.setCellValue(sumaCantidad);
        cantCell.setCellStyle(totalEnteroStyle);
    }

    private void escribirTablaDetalle(
            XSSFSheet sheet,
            List<GraficoDesgloseFila> validas,
            ColumnasExport columnas,
            String columnaEtiqueta,
            String etiquetaDetalle,
            int colInicio,
            int filaEtiqueta,
            int filaEncabezado,
            int filaInicioDatos,
            CellStyle headerStyle,
            CellStyle textoStyle,
            CellStyle monedaStyle,
            CellStyle enteroStyle,
            CellStyle totalTextoStyle,
            CellStyle totalMonedaStyle,
            CellStyle totalEnteroStyle
    ) {
        Row etiqueta = obtenerOCrearFila(sheet, filaEtiqueta);
        etiqueta.createCell(colInicio).setCellValue(etiquetaDetalle);

        Row header = obtenerOCrearFila(sheet, filaEncabezado);
        List<String> headers = construirEncabezadosDetalle(columnaEtiqueta, columnas);
        for (int c = 0; c < headers.size(); c++) {
            Cell cell = header.createCell(colInicio + c);
            cell.setCellValue(headers.get(c));
            cell.setCellStyle(headerStyle);
        }

        int fila = filaInicioDatos;
        for (GraficoDesgloseFila item : validas) {
            Row row = obtenerOCrearFila(sheet, fila++);
            escribirFilaDetalle(row, item, columnas, textoStyle, monedaStyle, enteroStyle, colInicio);
        }

        Row totalRow = obtenerOCrearFila(sheet, fila);
        escribirFilaTotalDetalle(
                totalRow,
                validas,
                columnas,
                totalTextoStyle,
                totalMonedaStyle,
                totalEnteroStyle,
                colInicio
        );
    }

    private void ajustarAnchos(
            XSSFSheet sheet,
            ColumnasExport columnas,
            String columnaEtiqueta,
            int colInicioDetalle,
            boolean graficoSoloTotal,
            boolean mostrarDetalle
    ) {
        int colsGrafico = graficoSoloTotal
                ? 3
                : construirEncabezadosGrafico(columnaEtiqueta, columnas).size();
        for (int i = 0; i < colsGrafico; i++) {
            ajustarAnchoColumna(sheet, i);
        }
        if (mostrarDetalle) {
            int colsDetalle = construirEncabezadosDetalle(columnaEtiqueta, columnas).size();
            for (int i = 0; i < colsDetalle; i++) {
                ajustarAnchoColumna(sheet, colInicioDetalle + i);
            }
        }
    }
}
