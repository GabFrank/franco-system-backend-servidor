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
import static com.franco.dev.service.grafico.excel.GraficoDesgloseExcelSupport.recolectarColumnas;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.COLUMNAS_SEPARACION_TABLAS;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.ajustarAnchoColumna;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloEncabezado;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloEntero;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearEstiloMoneda;
import static com.franco.dev.service.grafico.excel.GraficoExcelWorkbookSupport.crearGraficoBarras;
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
            List<GraficoDesgloseFila> filas
    ) throws IOException {
        List<GraficoDesgloseFila> validas = filas.stream()
                .sorted(Comparator.comparing(GraficoDesgloseFila::getTotal).reversed())
                .collect(Collectors.toList());

        if (validas.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }

        ColumnasExport columnas = recolectarColumnas(validas);
        int filaEncabezadoGrafico = filaInicioTablas();
        int filaInicioDatosGrafico = filaEncabezadoGrafico + 1;
        int filaFinDatosGrafico = filaInicioDatosGrafico + validas.size() - 1;
        int colInicioDetalle = construirEncabezadosGrafico(columnaEtiqueta, columnas).size()
                + COLUMNAS_SEPARACION_TABLAS;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(nombreHoja);
            CellStyle headerStyle = crearEstiloEncabezado(workbook);
            CellStyle monedaStyle = crearEstiloMoneda(workbook);
            CellStyle enteroStyle = crearEstiloEntero(workbook);

            escribirMetadatos(sheet, filtros);
            escribirTablaGrafico(
                    sheet,
                    validas,
                    columnas,
                    columnaEtiqueta,
                    filaEncabezadoGrafico,
                    filaInicioDatosGrafico,
                    headerStyle,
                    monedaStyle
            );

            int colFinSeries = columnas.mostrarTotalesAnho ? columnas.anhos.size() : 1;
            crearGraficoBarras(
                    sheet,
                    tituloGrafico,
                    filaEncabezadoGrafico,
                    filaInicioDatosGrafico,
                    filaFinDatosGrafico,
                    1,
                    colFinSeries
            );

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
                    monedaStyle,
                    enteroStyle
            );

            ajustarAnchos(sheet, columnas, columnaEtiqueta, colInicioDetalle);
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
            CellStyle monedaStyle
    ) {
        Row header = sheet.createRow(filaEncabezado);
        List<String> headersGrafico = construirEncabezadosGrafico(columnaEtiqueta, columnas);
        for (int c = 0; c < headersGrafico.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headersGrafico.get(c));
            cell.setCellStyle(headerStyle);
        }

        int fila = filaInicioDatos;
        for (GraficoDesgloseFila item : validas) {
            Row row = sheet.createRow(fila++);
            int col = 0;
            row.createCell(col++).setCellValue(item.getEtiqueta());
            if (columnas.mostrarTotalesAnho) {
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
            } else {
                Cell cell = row.createCell(col);
                cell.setCellValue(item.getTotal());
                cell.setCellStyle(monedaStyle);
            }
        }
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
            CellStyle monedaStyle,
            CellStyle enteroStyle
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
            escribirFilaDetalle(row, item, columnas, monedaStyle, enteroStyle, colInicio);
        }
    }

    private void ajustarAnchos(
            XSSFSheet sheet,
            ColumnasExport columnas,
            String columnaEtiqueta,
            int colInicioDetalle
    ) {
        int colsGrafico = construirEncabezadosGrafico(columnaEtiqueta, columnas).size();
        int colsDetalle = construirEncabezadosDetalle(columnaEtiqueta, columnas).size();
        for (int i = 0; i < colsGrafico; i++) {
            ajustarAnchoColumna(sheet, i);
        }
        for (int i = 0; i < colsDetalle; i++) {
            ajustarAnchoColumna(sheet, colInicioDetalle + i);
        }
    }
}
