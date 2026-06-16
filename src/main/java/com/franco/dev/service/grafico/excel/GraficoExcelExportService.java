package com.franco.dev.service.grafico.excel;

import com.franco.dev.domain.operaciones.VentaPorFuncionario;
import com.franco.dev.domain.grafico.IngresoGastoSerieGrafico;
import com.franco.dev.domain.grafico.VentasPorHoraSerieGrafico;
import com.franco.dev.graphql.grafico.input.GraficoExcelExportInput;
import com.franco.dev.graphql.grafico.input.PeriodoGraficoInput;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.service.grafico.GraficoAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.franco.dev.service.grafico.GraficoPeriodoUtil.normalizarUsuarioIds;

@Service
@RequiredArgsConstructor
public class GraficoExcelExportService {

    private final GraficoAggregationService graficoAggregationService;
    private final GraficoDesgloseBarraExcelExporter desgloseBarraExcelExporter;
    private final GraficoIngresoGastoExcelExporter ingresoGastoExcelExporter;
    private final GraficoVentasHoraExcelExporter ventasHoraExcelExporter;

    public byte[] exportar(GraficoExcelTipo tipo, GraficoExcelExportInput input) throws IOException {
        GraficoExcelFiltrosContext filtros = construirFiltros(tipo, input);
        List<PeriodoGraficoInput> periodos = input != null && input.getPeriodos() != null
                ? input.getPeriodos()
                : List.of();

        switch (tipo) {
            case VENTA_CIUDAD:
                return exportarCiudad(filtros, periodos);
            case VENTA_SUCURSAL:
                return exportarSucursal(filtros, periodos);
            case VENTA_FUNCIONARIO:
                return exportarFuncionario(filtros, input, periodos);
            case FORMA_PAGO:
                return exportarFormaPago(filtros, input, periodos);
            case GASTO_CATEGORIA:
                return exportarGastoCategoria(filtros, input, periodos);
            case PRODUCTOS_VENDIDOS:
                return exportarProductos(filtros, input, periodos);
            case INGRESO_GASTO:
                return exportarIngresoGasto(filtros, input);
            case VENTAS_HORA:
                return exportarVentasHora(filtros, input, periodos);
            default:
                throw new IllegalArgumentException("Tipo de gráfico no soportado: " + tipo);
        }
    }

    private byte[] exportarCiudad(GraficoExcelFiltrosContext filtros, List<PeriodoGraficoInput> periodos)
            throws IOException {
        List<GraficoDesgloseFila> filas = graficoAggregationService.ventasPorCiudadMulti(periodos).stream()
                .map(GraficoDesgloseFila::desdeCiudad)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Ventas por Ciudad",
                "Ciudad",
                "Detalle por ciudad",
                "Ventas por Ciudad",
                filas,
                GraficoExcelFormatoVisual.BARRAS_VERTICALES
        );
    }

    private byte[] exportarSucursal(GraficoExcelFiltrosContext filtros, List<PeriodoGraficoInput> periodos)
            throws IOException {
        List<GraficoDesgloseFila> filas = graficoAggregationService.ventasPorSucursalMulti(periodos).stream()
                .map(GraficoDesgloseFila::desdeSucursal)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Ventas por Sucursal",
                "Sucursal",
                "Detalle por sucursal",
                "Ventas por Sucursal",
                filas,
                GraficoExcelFormatoVisual.BARRAS_VERTICALES
        );
    }

    private static final int LIMITE_FUNCIONARIOS_SIN_FILTRO = 15;

    private byte[] exportarFuncionario(
            GraficoExcelFiltrosContext filtros,
            GraficoExcelExportInput input,
            List<PeriodoGraficoInput> periodos
    ) throws IOException {
        List<Long> usuarioIds = normalizarUsuarioIds(input.getUsuarioIds());
        List<VentaPorFuncionario> items = graficoAggregationService.ventasPorFuncionarioMulti(
                periodos,
                input.getSucIds(),
                usuarioIds);

        if (!usuarioIds.isEmpty()) {
            Set<Long> permitidos = new HashSet<>(usuarioIds);
            items = items.stream()
                    .filter(item -> item.getId() != null && permitidos.contains(item.getId()))
                    .collect(Collectors.toList());
        } else {
            items = items.stream()
                    .limit(LIMITE_FUNCIONARIOS_SIN_FILTRO)
                    .collect(Collectors.toList());
        }

        List<GraficoDesgloseFila> filas = items.stream()
                .map(GraficoDesgloseFila::desdeFuncionario)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Ventas por Funcionario",
                "Funcionario",
                "Detalle por funcionario",
                "Ventas por Funcionario",
                filas,
                GraficoExcelFormatoVisual.BARRAS_HORIZONTALES
        );
    }

    private byte[] exportarFormaPago(
            GraficoExcelFiltrosContext filtros,
            GraficoExcelExportInput input,
            List<PeriodoGraficoInput> periodos
    ) throws IOException {
        List<GraficoDesgloseFila> filas = graficoAggregationService.formaPagoEstadisticasMulti(
                        periodos,
                        input.getSucIds())
                .stream()
                .filter(item -> item.getCantidadTransacciones() != null
                        && item.getCantidadTransacciones() > 0)
                .map(GraficoDesgloseFila::desdeFormaPago)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Formas de Pago",
                "Forma de pago",
                "Detalle por forma de pago",
                "Formas de Pago",
                filas,
                GraficoExcelFormatoVisual.TORTA
        );
    }

    private byte[] exportarGastoCategoria(
            GraficoExcelFiltrosContext filtros,
            GraficoExcelExportInput input,
            List<PeriodoGraficoInput> periodos
    ) throws IOException {
        List<GraficoDesgloseFila> filas = graficoAggregationService.gastosPorCategoriaMulti(
                        periodos,
                        input.getSucIds())
                .stream()
                .map(GraficoDesgloseFila::desdeGastoCategoria)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Gastos por Categoría",
                "Categoría",
                "Detalle por categoría",
                "Gastos por Categoría",
                filas,
                GraficoExcelFormatoVisual.BARRAS_HORIZONTALES
        );
    }

    private byte[] exportarProductos(
            GraficoExcelFiltrosContext filtros,
            GraficoExcelExportInput input,
            List<PeriodoGraficoInput> periodos
    ) throws IOException {
        int limit = input.getLimit() != null && input.getLimit() > 0 ? input.getLimit() : 10;
        boolean ascendente = Boolean.TRUE.equals(input.getAscendente());
        List<GraficoDesgloseFila> filas = graficoAggregationService.productosMasVendidosMulti(
                        periodos,
                        input.getSucIds(),
                        limit,
                        input.getFamiliaId(),
                        ascendente,
                        input.getProductoIds())
                .stream()
                .map(GraficoDesgloseFila::desdeProducto)
                .collect(Collectors.toList());
        return desgloseBarraExcelExporter.exportar(
                filtros,
                "Productos Vendidos",
                "Producto",
                "Detalle por producto",
                "Productos Vendidos",
                filas,
                GraficoExcelFormatoVisual.TORTA
        );
    }

    private byte[] exportarIngresoGasto(GraficoExcelFiltrosContext filtros, GraficoExcelExportInput input)
            throws IOException {
        List<Integer> anios = input.getAnios();
        if (anios == null || anios.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un año");
        }
        List<IngresoGastoSerieGrafico> series = graficoAggregationService.ingresosGastosPorMesMulti(
                anios,
                input.getSucIds()
        );
        return ingresoGastoExcelExporter.exportar(filtros, series);
    }

    private byte[] exportarVentasHora(
            GraficoExcelFiltrosContext filtros,
            GraficoExcelExportInput input,
            List<PeriodoGraficoInput> periodos
    ) throws IOException {
        List<VentasPorHoraSerieGrafico> series = graficoAggregationService.ventasPorHoraMulti(
                periodos,
                input.getSucIds()
        );
        return ventasHoraExcelExporter.exportar(filtros, series);
    }

    private GraficoExcelFiltrosContext construirFiltros(GraficoExcelTipo tipo, GraficoExcelExportInput input) {
        String titulo;
        switch (tipo) {
            case VENTA_CIUDAD:
                titulo = "Ventas por Ciudad";
                break;
            case VENTA_SUCURSAL:
                titulo = "Ventas por Sucursal";
                break;
            case VENTA_FUNCIONARIO:
                titulo = "Ventas por Funcionario";
                break;
            case FORMA_PAGO:
                titulo = "Formas de Pago";
                break;
            case GASTO_CATEGORIA:
                titulo = "Gastos por Categoría";
                break;
            case PRODUCTOS_VENDIDOS:
                titulo = "Productos Vendidos";
                break;
            case INGRESO_GASTO:
                titulo = "Ingresos vs Gastos";
                break;
            case VENTAS_HORA:
                titulo = "Ventas por Hora";
                break;
            default:
                titulo = "Gráfico";
                break;
        }
        return GraficoExcelFiltrosContext.builder()
                .titulo(titulo)
                .filtroAnhos(input != null ? input.getFiltroAnhos() : null)
                .filtroMeses(input != null ? input.getFiltroMeses() : null)
                .filtroRangoDias(input != null ? input.getFiltroRangoDias() : null)
                .filtroSucursales(input != null ? input.getFiltroSucursales() : null)
                .filtroExtra(input != null ? input.getFiltroExtra() : null)
                .build();
    }
}
