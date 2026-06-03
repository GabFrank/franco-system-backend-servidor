package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.grafico.VentasPorHoraSerieGrafico;
import com.franco.dev.domain.operaciones.VentaPorHora;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
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
public class GraficoVentasHoraExcelExporter {

    public byte[] exportar(GraficoExcelFiltrosContext filtros, List<VentasPorHoraSerieGrafico> series)
            throws IOException {
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("No hay datos para exportar");
        }

        List<String> nombresSeries = new ArrayList<>();
        List<double[]> totalesSeries = new ArrayList<>();
        List<double[]> cantidadesSeries = new ArrayList<>();

        for (VentasPorHoraSerieGrafico serie : series) {
            nombresSeries.add(etiquetaSerie(serie));
            double[] totales = new double[24];
            double[] cantidades = new double[24];
            if (serie.getDatos() != null) {
                for (VentaPorHora dato : serie.getDatos()) {
                    if (dato.getHora() == null || dato.getHora() < 0 || dato.getHora() > 23) {
                        continue;
                    }
                    int h = dato.getHora();
                    totales[h] += dato.getTotal() != null ? dato.getTotal() : 0;
                    cantidades[h] += dato.getCantidad() != null ? dato.getCantidad() : 0;
                }
            }
            totalesSeries.add(totales);
            cantidadesSeries.add(cantidades);
        }

        int filaEncabezado = filaInicioTablas();
        int filaInicioDatos = filaEncabezado + 1;
        int filaFinDatos = filaInicioDatos + 23;
        int colInicioDetalle = nombresSeries.size() + 1 + 2;

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Ventas por Hora");
            CellStyle headerStyle = crearEstiloEncabezado(workbook);
            CellStyle monedaStyle = crearEstiloMoneda(workbook);
            CellStyle enteroStyle = crearEstiloEntero(workbook);

            escribirMetadatos(sheet, filtros);

            Row header = sheet.createRow(filaEncabezado);
            header.createCell(0).setCellValue("Hora");
            header.getCell(0).setCellStyle(headerStyle);
            for (int s = 0; s < nombresSeries.size(); s++) {
                Cell cell = header.createCell(s + 1);
                cell.setCellValue(nombresSeries.get(s));
                cell.setCellStyle(headerStyle);
            }

            for (int h = 0; h < 24; h++) {
                Row row = sheet.createRow(filaInicioDatos + h);
                row.createCell(0).setCellValue(String.format("%02d:00", h));
                for (int s = 0; s < nombresSeries.size(); s++) {
                    Cell cell = row.createCell(s + 1);
                    cell.setCellValue(totalesSeries.get(s)[h]);
                    cell.setCellStyle(monedaStyle);
                }
            }

            if (!nombresSeries.isEmpty()) {
                crearGraficoBarras(
                        sheet,
                        "Ventas por Hora",
                        filaEncabezado,
                        filaInicioDatos,
                        filaFinDatos,
                        1,
                        nombresSeries.size()
                );
            }

            Row etiqueta = obtenerOCrearFila(sheet, filaEncabezado - 1);
            etiqueta.createCell(colInicioDetalle).setCellValue("Detalle por hora");

            Row headerDet = obtenerOCrearFila(sheet, filaEncabezado);
            List<String> headersDet = new ArrayList<>();
            headersDet.add("Hora");
            for (String nombre : nombresSeries) {
                headersDet.add(nombre + " - Monto");
                headersDet.add(nombre + " - Ventas");
            }
            for (int c = 0; c < headersDet.size(); c++) {
                Cell cell = headerDet.createCell(colInicioDetalle + c);
                cell.setCellValue(headersDet.get(c));
                cell.setCellStyle(headerStyle);
            }

            for (int h = 0; h < 24; h++) {
                Row row = obtenerOCrearFila(sheet, filaInicioDatos + h);
                int col = colInicioDetalle;
                row.createCell(col++).setCellValue(String.format("%02d:00", h));
                for (int s = 0; s < nombresSeries.size(); s++) {
                    Cell monto = row.createCell(col++);
                    monto.setCellValue(totalesSeries.get(s)[h]);
                    monto.setCellStyle(monedaStyle);
                    Cell cant = row.createCell(col++);
                    cant.setCellValue(cantidadesSeries.get(s)[h]);
                    cant.setCellStyle(enteroStyle);
                }
            }

            for (int i = 0; i < nombresSeries.size() + 1; i++) {
                ajustarAnchoColumna(sheet, i);
            }
            for (int i = 0; i < headersDet.size(); i++) {
                ajustarAnchoColumna(sheet, colInicioDetalle + i);
            }

            return escribirWorkbook(workbook);
        }
    }

    private String etiquetaSerie(VentasPorHoraSerieGrafico serie) {
        String base = serie.getEtiqueta() != null ? serie.getEtiqueta() : "Serie";
        if (serie.getSucursalNombre() != null && !serie.getSucursalNombre().isBlank()) {
            return base + " · " + serie.getSucursalNombre();
        }
        return base;
    }
}
