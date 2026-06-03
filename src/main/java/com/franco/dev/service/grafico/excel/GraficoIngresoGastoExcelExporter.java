package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.financiero.GastoPorMes;
import com.franco.dev.domain.grafico.IngresoGastoSerieGrafico;
import com.franco.dev.domain.operaciones.VentaPorMes;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

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
public class GraficoIngresoGastoExcelExporter {

    private static final String[] MESES = {
            "Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    public byte[] exportar(GraficoExcelFiltrosContext filtros, List<IngresoGastoSerieGrafico> series)
            throws IOException {
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }

        double[] ingresos = new double[12];
        double[] gastos = new double[12];
        double[] cantIngresos = new double[12];
        double[] cantGastos = new double[12];

        for (IngresoGastoSerieGrafico serie : series) {
            acumularMeses(serie.getIngresos(), ingresos, cantIngresos);
            acumularMesesGasto(serie.getGastos(), gastos, cantGastos);
        }

        int filaEncabezado = filaInicioTablas();
        int filaInicioDatos = filaEncabezado + 1;
        int filaFinDatos = filaInicioDatos + 11;
        int colInicioDetalle = 6;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Ingresos vs Gastos");
            CellStyle headerStyle = crearEstiloEncabezado(workbook);
            CellStyle monedaStyle = crearEstiloMoneda(workbook);
            CellStyle enteroStyle = crearEstiloEntero(workbook);

            escribirMetadatos(sheet, filtros);

            Row header = sheet.createRow(filaEncabezado);
            String[] cols = {"Mes", "Ingresos", "Gastos"};
            for (int c = 0; c < cols.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(cols[c]);
                cell.setCellStyle(headerStyle);
            }

            for (int mes = 0; mes < 12; mes++) {
                Row row = sheet.createRow(filaInicioDatos + mes);
                row.createCell(0).setCellValue(MESES[mes]);
                Cell ing = row.createCell(1);
                ing.setCellValue(ingresos[mes]);
                ing.setCellStyle(monedaStyle);
                Cell gas = row.createCell(2);
                gas.setCellValue(gastos[mes]);
                gas.setCellStyle(monedaStyle);
            }

            crearGraficoBarras(sheet, "Ingresos vs Gastos", filaEncabezado, filaInicioDatos, filaFinDatos, 1, 2);

            Row etiqueta = obtenerOCrearFila(sheet, filaEncabezado - 1);
            etiqueta.createCell(colInicioDetalle).setCellValue("Detalle por mes");

            Row headerDet = obtenerOCrearFila(sheet, filaEncabezado);
            String[] colsDet = {"Mes", "Ingresos", "Cant. ventas", "Gastos", "Cant. gastos"};
            for (int c = 0; c < colsDet.length; c++) {
                Cell cell = headerDet.createCell(colInicioDetalle + c);
                cell.setCellValue(colsDet[c]);
                cell.setCellStyle(headerStyle);
            }

            for (int mes = 0; mes < 12; mes++) {
                Row row = obtenerOCrearFila(sheet, filaInicioDatos + mes);
                int col = colInicioDetalle;
                row.createCell(col++).setCellValue(MESES[mes]);
                Cell ing = row.createCell(col++);
                ing.setCellValue(ingresos[mes]);
                ing.setCellStyle(monedaStyle);
                Cell cIng = row.createCell(col++);
                cIng.setCellValue(cantIngresos[mes]);
                cIng.setCellStyle(enteroStyle);
                Cell gas = row.createCell(col++);
                gas.setCellValue(gastos[mes]);
                gas.setCellStyle(monedaStyle);
                Cell cGas = row.createCell(col);
                cGas.setCellValue(cantGastos[mes]);
                cGas.setCellStyle(enteroStyle);
            }

            for (int i = 0; i < 3; i++) {
                ajustarAnchoColumna(sheet, i);
            }
            for (int i = 0; i < colsDet.length; i++) {
                ajustarAnchoColumna(sheet, colInicioDetalle + i);
            }

            return escribirWorkbook(workbook);
        }
    }

    private void acumularMeses(List<VentaPorMes> items, double[] totales, double[] cantidades) {
        if (items == null) {
            return;
        }
        for (VentaPorMes item : items) {
            if (item.getMes() == null || item.getMes() < 1 || item.getMes() > 12) {
                continue;
            }
            int idx = item.getMes() - 1;
            totales[idx] += item.getTotal() != null ? item.getTotal() : 0;
            cantidades[idx] += item.getCantidad() != null ? item.getCantidad() : 0;
        }
    }

    private void acumularMesesGasto(List<GastoPorMes> items, double[] totales, double[] cantidades) {
        if (items == null) {
            return;
        }
        for (GastoPorMes item : items) {
            if (item.getMes() == null || item.getMes() < 1 || item.getMes() > 12) {
                continue;
            }
            int idx = item.getMes() - 1;
            totales[idx] += item.getTotal() != null ? item.getTotal() : 0;
            cantidades[idx] += item.getCantidad() != null ? item.getCantidad() : 0;
        }
    }
}
