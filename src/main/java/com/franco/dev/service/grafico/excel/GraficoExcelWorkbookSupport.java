package com.franco.dev.service.grafico.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.AxisCrossBetween;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartLegend;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.STPresetLineDashVal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class GraficoExcelWorkbookSupport {

    static final int FILAS_METADATOS = 5;
    static final int FILA_ZONA_GRAFICO = 5;
    static final int FILAS_ZONA_GRAFICO = 20;
    static final int COLUMNAS_SEPARACION_TABLAS = 2;

    private GraficoExcelWorkbookSupport() {
    }

    static byte[] escribirWorkbook(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    static void escribirMetadatos(XSSFSheet sheet, GraficoExcelFiltrosContext filtros) {
        String generado = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        crearFila(sheet, 0, filtros.getTitulo());
        crearFila(sheet, 1, "Generado", generado);
        crearFila(sheet, 2, "Años", nulo(filtros.getFiltroAnhos()));
        crearFila(sheet, 3, "Meses", nulo(filtros.getFiltroMeses()));
        crearFila(sheet, 4, "Rango de días", nulo(filtros.getFiltroRangoDias()));
        if (filtros.getFiltroSucursales() != null && !filtros.getFiltroSucursales().isBlank()) {
            crearFila(sheet, 5, "Sucursales", filtros.getFiltroSucursales());
        }
        if (filtros.getFiltroExtra() != null && !filtros.getFiltroExtra().isBlank()) {
            int fila = sheet.getLastRowNum() + 1;
            crearFila(sheet, fila, "Otros filtros", filtros.getFiltroExtra());
        }
    }

    static int filaInicioTablas() {
        return FILAS_METADATOS + FILAS_ZONA_GRAFICO;
    }

    static void crearGraficoBarras(
            XSSFSheet sheet,
            String tituloGrafico,
            int filaEncabezadoGrafico,
            int filaInicioDatos,
            int filaFinDatos,
            int colInicioSeries,
            int colFinSeries
    ) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0, 0, FILA_ZONA_GRAFICO, 11, FILA_ZONA_GRAFICO + FILAS_ZONA_GRAFICO);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(tituloGrafico);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);

        var categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(filaInicioDatos, filaFinDatos, 0, 0)
        );

        XDDFChartData data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        for (int col = colInicioSeries; col <= colFinSeries; col++) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet,
                    new CellRangeAddress(filaInicioDatos, filaFinDatos, col, col)
            );
            XDDFChartData.Series series = data.addSeries(categories, values);
            Cell headerCell = sheet.getRow(filaEncabezadoGrafico).getCell(col);
            String titulo = headerCell != null ? headerCell.getStringCellValue() : "Serie";
            series.setTitle(titulo, null);
        }

        chart.plot(data);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.COL);
        bar.setVaryColors(true);

        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        if (plotArea != null && !plotArea.isSetLayout()) {
            plotArea.addNewLayout();
        }
    }

    static void crearGraficoLineas(
            XSSFSheet sheet,
            String tituloGrafico,
            int filaEncabezadoGrafico,
            int filaInicioDatos,
            int filaFinDatos,
            int colInicioSeries,
            int colFinSeries
    ) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0, 0, FILA_ZONA_GRAFICO, 11, FILA_ZONA_GRAFICO + FILAS_ZONA_GRAFICO);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(tituloGrafico);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);

        var categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(filaInicioDatos, filaFinDatos, 0, 0)
        );

        XDDFChartData data = chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        for (int col = colInicioSeries; col <= colFinSeries; col++) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet,
                    new CellRangeAddress(filaInicioDatos, filaFinDatos, col, col)
            );
            XDDFChartData.Series series = data.addSeries(categories, values);
            Cell headerCell = sheet.getRow(filaEncabezadoGrafico).getCell(col);
            String titulo = headerCell != null ? headerCell.getStringCellValue() : "Serie";
            series.setTitle(titulo, null);
        }

        chart.plot(data);
        aplicarEstiloLineasVentasHora(chart, sheet, filaEncabezadoGrafico, colInicioSeries, colFinSeries);

        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        if (plotArea != null && !plotArea.isSetLayout()) {
            plotArea.addNewLayout();
        }
    }

    private static void aplicarEstiloLineasVentasHora(
            XSSFChart chart,
            XSSFSheet sheet,
            int filaEncabezadoGrafico,
            int colInicioSeries,
            int colFinSeries
    ) {
        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        if (plotArea == null || plotArea.sizeOfLineChartArray() == 0) {
            return;
        }

        CTLineChart lineChart = plotArea.getLineChartArray(0);
        int indiceSerie = 0;
        for (int col = colInicioSeries; col <= colFinSeries; col++) {
            if (indiceSerie >= lineChart.sizeOfSerArray()) {
                break;
            }
            CTLineSer ser = lineChart.getSerArray(indiceSerie);
            if (!ser.isSetSmooth()) {
                ser.addNewSmooth();
            }
            ser.getSmooth().setVal(true);

            Cell headerCell = sheet.getRow(filaEncabezadoGrafico).getCell(col);
            String titulo = headerCell != null ? headerCell.getStringCellValue() : "";
            if (titulo.startsWith("Ayer")) {
                aplicarLineaPunteada(ser);
            }
            indiceSerie++;
        }
    }

    private static void aplicarLineaPunteada(CTLineSer ser) {
        CTShapeProperties shapeProperties = ser.isSetSpPr() ? ser.getSpPr() : ser.addNewSpPr();
        CTLineProperties lineProperties = shapeProperties.isSetLn() ? shapeProperties.getLn() : shapeProperties.addNewLn();
        lineProperties.addNewPrstDash().setVal(STPresetLineDashVal.DASH);
    }

    static void crearGraficoBarrasHorizontales(
            XSSFSheet sheet,
            String tituloGrafico,
            int filaEncabezadoGrafico,
            int filaInicioDatos,
            int filaFinDatos,
            int colInicioSeries,
            int colFinSeries
    ) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0, 0, FILA_ZONA_GRAFICO, 11, FILA_ZONA_GRAFICO + FILAS_ZONA_GRAFICO);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(tituloGrafico);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis leftAxis = chart.createCategoryAxis(AxisPosition.LEFT);
        XDDFValueAxis bottomAxis = chart.createValueAxis(AxisPosition.BOTTOM);
        bottomAxis.setCrossBetween(AxisCrossBetween.BETWEEN);

        var categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(filaInicioDatos, filaFinDatos, 0, 0)
        );

        XDDFChartData data = chart.createData(ChartTypes.BAR, leftAxis, bottomAxis);
        for (int col = colInicioSeries; col <= colFinSeries; col++) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet,
                    new CellRangeAddress(filaInicioDatos, filaFinDatos, col, col)
            );
            XDDFChartData.Series series = data.addSeries(categories, values);
            Cell headerCell = sheet.getRow(filaEncabezadoGrafico).getCell(col);
            String titulo = headerCell != null ? headerCell.getStringCellValue() : "Serie";
            series.setTitle(titulo, null);
        }

        chart.plot(data);
        XDDFBarChartData bar = (XDDFBarChartData) data;
        bar.setBarDirection(BarDirection.BAR);
        bar.setVaryColors(true);

        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        if (plotArea != null && !plotArea.isSetLayout()) {
            plotArea.addNewLayout();
        }
    }

    static void crearGraficoTorta(
            XSSFSheet sheet,
            String tituloGrafico,
            int filaEncabezadoGrafico,
            int filaInicioDatos,
            int filaFinDatos,
            int colValores
    ) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(
                0, 0, 0, 0, 0, FILA_ZONA_GRAFICO, 11, FILA_ZONA_GRAFICO + FILAS_ZONA_GRAFICO);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(tituloGrafico);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        var categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet,
                new CellRangeAddress(filaInicioDatos, filaFinDatos, 0, 0)
        );
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet,
                new CellRangeAddress(filaInicioDatos, filaFinDatos, colValores, colValores)
        );

        // POI 4.1.0 no soporta createData(PIE, null, null); el constructor directo evita ejes inválidos.
        XDDFPieChartData data = new XDDFPieChartData(chart.getCTChart().getPlotArea().addNewPieChart());
        data.setVaryColors(true);
        XDDFChartData.Series series = data.addSeries(categories, values);
        Cell headerCell = sheet.getRow(filaEncabezadoGrafico).getCell(colValores);
        String titulo = headerCell != null ? headerCell.getStringCellValue() : "Total";
        series.setTitle(titulo, null);
        chart.plot(data);

        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        if (plotArea != null && !plotArea.isSetLayout()) {
            plotArea.addNewLayout();
        }
    }

    static void crearGraficoSegunFormato(
            GraficoExcelFormatoVisual formato,
            XSSFSheet sheet,
            String tituloGrafico,
            int filaEncabezadoGrafico,
            int filaInicioDatos,
            int filaFinDatos,
            int colInicioSeries,
            int colFinSeries
    ) {
        switch (formato) {
            case BARRAS_HORIZONTALES:
                crearGraficoBarrasHorizontales(
                        sheet,
                        tituloGrafico,
                        filaEncabezadoGrafico,
                        filaInicioDatos,
                        filaFinDatos,
                        colInicioSeries,
                        colFinSeries
                );
                break;
            case TORTA:
                crearGraficoTorta(
                        sheet,
                        tituloGrafico,
                        filaEncabezadoGrafico,
                        filaInicioDatos,
                        filaFinDatos,
                        colInicioSeries
                );
                break;
            case LINEAS:
                crearGraficoLineas(
                        sheet,
                        tituloGrafico,
                        filaEncabezadoGrafico,
                        filaInicioDatos,
                        filaFinDatos,
                        colInicioSeries,
                        colFinSeries
                );
                break;
            case BARRAS_VERTICALES:
            default:
                crearGraficoBarras(
                        sheet,
                        tituloGrafico,
                        filaEncabezadoGrafico,
                        filaInicioDatos,
                        filaFinDatos,
                        colInicioSeries,
                        colFinSeries
                );
                break;
        }
    }

    static Row obtenerOCrearFila(XSSFSheet sheet, int fila) {
        Row row = sheet.getRow(fila);
        return row != null ? row : sheet.createRow(fila);
    }

    static void crearFila(XSSFSheet sheet, int rowIndex, String... valores) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < valores.length; i++) {
            row.createCell(i).setCellValue(valores[i]);
        }
    }

    static CellStyle crearEstiloEncabezado(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    static CellStyle crearEstiloMoneda(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    static CellStyle crearEstiloEntero(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return style;
    }

    static void ajustarAnchoColumna(XSSFSheet sheet, int indiceColumna) {
        sheet.autoSizeColumn(indiceColumna);
        int width = sheet.getColumnWidth(indiceColumna);
        sheet.setColumnWidth(indiceColumna, Math.min(width + 512, 28 * 256));
    }

    private static String nulo(String valor) {
        return valor != null && !valor.isBlank() ? valor : "—";
    }
}
