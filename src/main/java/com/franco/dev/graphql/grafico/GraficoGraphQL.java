package com.franco.dev.graphql.grafico;

import com.franco.dev.domain.financiero.GastoPorCategoria;
import com.franco.dev.domain.grafico.IngresoGastoSerieGrafico;
import com.franco.dev.domain.grafico.VentasPorHoraSerieGrafico;
import com.franco.dev.domain.operaciones.VentaPorFuncionario;
import com.franco.dev.domain.operaciones.VentaPorSucursal;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.graphql.grafico.input.PeriodoGraficoInput;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.service.grafico.GraficoAggregationService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GraficoGraphQL implements GraphQLQueryResolver {

    private final GraficoAggregationService graficoAggregationService;

    public List<VentaPorFuncionario> ventasPorFuncionarioMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds,
            List<Long> usuarioIds) {
        return graficoAggregationService.ventasPorFuncionarioMulti(periodos, sucIds, usuarioIds);
    }

    public List<FormaPagoEstadistica> formaPagoEstadisticasMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        return graficoAggregationService.formaPagoEstadisticasMulti(periodos, sucIds);
    }

    public List<GastoPorCategoria> gastosPorCategoriaMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        return graficoAggregationService.gastosPorCategoriaMulti(periodos, sucIds);
    }

    public List<VentaPorSucursal> ventasPorSucursalMulti(List<PeriodoGraficoInput> periodos) {
        return graficoAggregationService.ventasPorSucursalMulti(periodos);
    }

    public List<ProductoVendidoEstadistica> productosMasVendidosMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds,
            Integer limit,
            Long familiaId,
            Boolean ascendente,
            List<Long> productoIds) {
        return graficoAggregationService.productosMasVendidosMulti(
                periodos, sucIds, limit, familiaId, ascendente, productoIds);
    }

    public List<IngresoGastoSerieGrafico> ingresosGastosPorMesMulti(
            List<Integer> anios,
            List<Long> sucIds) {
        return graficoAggregationService.ingresosGastosPorMesMulti(anios, sucIds);
    }

    public List<VentasPorHoraSerieGrafico> ventasPorHoraMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        return graficoAggregationService.ventasPorHoraMulti(periodos, sucIds);
    }
}
