package com.franco.dev.service.grafico.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
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

    private static final byte[] COLOR_ENCABEZADO = new byte[]{(byte) 31, (byte) 78, (byte) 121};
    private static final byte[] COLOR_ENCABEZADO_TEXTO = new byte[]{(byte) 255, (byte) 255, (byte) 255};
    private static final byte[] COLOR_TOTAL = new byte[]{(byte) 255, (byte) 242, (byte) 204};

    private GraficoExcelWorkbookSupport() {
    }

    static byte[] escribirWorkbook(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    static void escribirMetadatos(XSSFSheet sheet, GraficoExcelFiltrosContext filtros) {
        XSSFWorkbook workbook = (XSSFWorkbook) sheet.getWorkbook();
        CellStyle tituloStyle = crearEstiloTitulo(workbook);
        CellStyle labelStyle = crearEstiloMetaLabel(workbook);

        String generado = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Row tituloRow = sheet.createRow(0);
        Cell tituloCell = tituloRow.createCell(0);
        tituloCell.setCellValue(filtros.getTitulo());
        tituloCell.setCellStyle(tituloStyle);

        crearFilaMeta(sheet, 1, labelStyle, "Generado", generado);
        crearFilaMeta(sheet, 2, labelStyle, "Años", nulo(filtros.getFiltroAnhos()));
        crearFilaMeta(sheet, 3, labelStyle, "Meses", nulo(filtros.getFiltroMeses()));
        crearFilaMeta(sheet, 4, labelStyle, "Rango de días", nulo(filtros.getFiltroRangoDias()));
        if (filtros.getFiltroSucursales() != null && !filtros.getFiltroSucursales().isBlank()) {
            crearFilaMeta(sheet, 5, labelStyle, "Sucursales", filtros.getFiltroSucursales());
        }
        if (filtros.getFiltroExtra() != null && !filtros.getFiltroExtra().isBlank()) {
            int fila = sheet.getLastRowNum() + 1;
            crearFilaMeta(sheet, fila, labelStyle, "Otros filtros", filtros.getFiltroExtra());
        }
    }

    private static void crearFilaMeta(XSSFSheet sheet, int rowIndex, CellStyle labelStyle, String etiqueta, String valor) {
        Row row = sheet.createRow(rowIndex);
        Cell label = row.createCell(0);
        label.setCellValue(etiqueta);
        label.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(valor);
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

    static CellStyle crearEstiloTitulo(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(new XSSFColor(COLOR_ENCABEZADO, null));
        style.setFont(font);
        return style;
    }

    static CellStyle crearEstiloMetaLabel(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    static CellStyle crearEstiloEncabezado(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(COLOR_ENCABEZADO_TEXTO, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(COLOR_ENCABEZADO, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloTexto(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloMoneda(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloEntero(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloTotalTexto(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(COLOR_TOTAL, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloTotalMoneda(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        style.setFillForegroundColor(new XSSFColor(COLOR_TOTAL, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(style);
        return style;
    }

    static CellStyle crearEstiloTotalEntero(XSSFWorkbook workbook) {
        return crearEstiloTotalMoneda(workbook);
    }

    private static void aplicarBordes(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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
