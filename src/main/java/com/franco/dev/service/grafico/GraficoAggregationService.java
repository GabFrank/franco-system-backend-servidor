package com.franco.dev.service.grafico;

import com.franco.dev.domain.financiero.GastoPorCategoria;
import com.franco.dev.domain.grafico.DesglosePeriodoGrafico;
import com.franco.dev.domain.grafico.IngresoGastoSerieGrafico;
import com.franco.dev.domain.grafico.VentasPorHoraSerieGrafico;
import com.franco.dev.domain.operaciones.VentaPorFuncionario;
import com.franco.dev.domain.operaciones.VentaPorCiudad;
import com.franco.dev.domain.operaciones.VentaPorSucursal;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.graphql.financiero.dto.FormaPagoMonedaDesglose;
import com.franco.dev.graphql.grafico.input.PeriodoGraficoInput;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.franco.dev.service.grafico.GraficoPeriodoUtil.agregarDesglose;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.agregarDesgloseAnho;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.esMultiPeriodo;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.extraerAnho;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.fusionarSucursalesTexto;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.normalizarSucIds;
import static com.franco.dev.service.grafico.GraficoPeriodoUtil.normalizarUsuarioIds;
import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Service
@RequiredArgsConstructor
public class GraficoAggregationService {

    private final VentaService ventaService;
    private final CobroDetalleService cobroDetalleService;
    private final VentaItemService ventaItemService;
    private final GastoService gastoService;
    private final SucursalService sucursalService;

    public List<VentaPorFuncionario> ventasPorFuncionarioMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds,
            List<Long> usuarioIds) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);
        List<Long> sucursales = normalizarSucIds(sucIds);
        List<Long> usuarios = normalizarUsuarioIds(usuarioIds);

        Map<Long, VentaPorFuncionario> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            for (Long sucId : sucursales) {
                List<VentaPorFuncionario> items = consultarVentasPorFuncionario(
                        periodo.getInicio(),
                        periodo.getFin(),
                        sucId,
                        usuarios);
                acumularVentasPorFuncionario(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
            }
        }

        return ordenarPorTotalDesc(mapa.values());
    }

    public List<FormaPagoEstadistica> formaPagoEstadisticasMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);
        List<Long> sucursales = normalizarSucIds(sucIds);

        Map<String, FormaPagoEstadistica> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            for (Long sucId : sucursales) {
                LocalDateTime inicio = stringToDate(periodo.getInicio());
                LocalDateTime fin = stringToDate(periodo.getFin());
                List<FormaPagoEstadistica> items = cobroDetalleService
                        .obtenerEstadisticasFormaPagoConFiltros(inicio, fin, sucId);
                acumularFormaPago(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
            }
        }

        recalcularPorcentajeFormaPago(mapa.values());
        return new ArrayList<>(mapa.values());
    }

    public List<GastoPorCategoria> gastosPorCategoriaMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);
        List<Long> sucursales = normalizarSucIds(sucIds);

        Map<String, GastoPorCategoria> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            for (Long sucId : sucursales) {
                List<GastoPorCategoria> items = gastoService.gastosPorCategoria(
                        periodo.getInicio(),
                        periodo.getFin(),
                        sucId);
                acumularGastosPorCategoria(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
            }
        }

        return new ArrayList<>(mapa.values());
    }

    public List<VentaPorSucursal> ventasPorSucursalMulti(List<PeriodoGraficoInput> periodos) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);

        Map<String, VentaPorSucursal> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            List<VentaPorSucursal> items = ventaService.ventaPorSucursal(
                    periodo.getInicio(),
                    periodo.getFin());
            acumularVentasPorSucursal(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
        }

        return ordenarVentasPorSucursalDesc(mapa.values());
    }

    public List<VentaPorCiudad> ventasPorCiudadMulti(List<PeriodoGraficoInput> periodos) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);

        Map<String, VentaPorCiudad> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            List<VentaPorCiudad> items = ventaService.ventaPorCiudad(
                    periodo.getInicio(),
                    periodo.getFin());
            acumularVentasPorCiudad(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
        }

        return ordenarVentasPorCiudadDesc(mapa.values());
    }

    public List<ProductoVendidoEstadistica> productosMasVendidosMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds,
            Integer limit,
            Long familiaId,
            Boolean ascendente,
            List<Long> productoIds) {
        validarPeriodos(periodos);
        boolean multiPeriodo = esMultiPeriodo(periodos);
        List<Long> sucursales = normalizarSucIds(sucIds);
        int limiteConsulta = limit != null && limit > 0 ? limit : 10;

        Map<Long, ProductoVendidoEstadistica> mapa = new LinkedHashMap<>();

        for (PeriodoGraficoInput periodo : periodos) {
            Integer anio = extraerAnho(periodo.getInicio());
            for (Long sucId : sucursales) {
                LocalDateTime inicio = stringToDate(periodo.getInicio());
                LocalDateTime fin = stringToDate(periodo.getFin());
                List<ProductoVendidoEstadistica> items = ventaItemService.obtenerProductosMasVendidos(
                        inicio,
                        fin,
                        limiteConsulta,
                        sucId,
                        familiaId,
                        ascendente,
                        null,
                        productoIds);
                acumularProductos(mapa, items, periodo.getEtiqueta(), multiPeriodo, anio);
            }
        }

        recalcularPorcentajeProductos(mapa.values());
        return ordenarProductos(mapa.values(), ascendente, limiteConsulta);
    }

    public List<IngresoGastoSerieGrafico> ingresosGastosPorMesMulti(
            List<Integer> anios,
            List<Long> sucIds) {
        if (anios == null || anios.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un año");
        }
        List<Long> sucursales = normalizarSucIds(sucIds);
        List<IngresoGastoSerieGrafico> series = new ArrayList<>();

        for (Integer anio : anios) {
            for (Long sucId : sucursales) {
                IngresoGastoSerieGrafico serie = new IngresoGastoSerieGrafico();
                serie.setAnio(anio);
                serie.setSucId(sucId);
                serie.setSucursalNombre(resolverNombreSucursal(sucId));
                serie.setIngresos(ventaService.ventasPorMes(anio, sucId));
                serie.setGastos(gastoService.gastosPorMes(anio, sucId));
                series.add(serie);
            }
        }

        return series;
    }

    public List<VentasPorHoraSerieGrafico> ventasPorHoraMulti(
            List<PeriodoGraficoInput> periodos,
            List<Long> sucIds) {
        validarPeriodos(periodos);
        List<Long> sucursales = normalizarSucIds(sucIds);
        List<VentasPorHoraSerieGrafico> series = new ArrayList<>();

        for (PeriodoGraficoInput periodo : periodos) {
            for (Long sucId : sucursales) {
                VentasPorHoraSerieGrafico serie = new VentasPorHoraSerieGrafico();
                serie.setSucId(sucId);
                serie.setSucursalNombre(resolverNombreSucursal(sucId));
                serie.setEtiqueta(periodo.getEtiqueta());
                serie.setFecha(extraerFecha(periodo.getInicio()));
                serie.setDatos(ventaService.ventasPorHora(extraerFecha(periodo.getInicio()), sucId));
                series.add(serie);
            }
        }

        return series;
    }

    private List<VentaPorFuncionario> consultarVentasPorFuncionario(
            String inicio,
            String fin,
            Long sucId,
            List<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return ventaService.ventasPorFuncionario(inicio, fin, sucId, null);
        }
        if (usuarioIds.size() == 1) {
            return ventaService.ventasPorFuncionario(inicio, fin, sucId, usuarioIds.get(0));
        }

        Map<Long, VentaPorFuncionario> mapa = new HashMap<>();
        for (Long usuarioId : usuarioIds) {
            List<VentaPorFuncionario> items = ventaService.ventasPorFuncionario(
                    inicio, fin, sucId, usuarioId);
            acumularVentasPorFuncionario(mapa, items, null, false, null);
        }
        return new ArrayList<>(mapa.values());
    }

    private void acumularVentasPorFuncionario(
            Map<Long, VentaPorFuncionario> mapa,
            List<VentaPorFuncionario> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (VentaPorFuncionario item : items) {
            if (item.getId() == null) {
                continue;
            }
            double total = item.getTotal() != null ? item.getTotal() : 0.0;
            long cantidad = item.getCantidad() != null ? item.getCantidad() : 0L;

            VentaPorFuncionario acc = mapa.get(item.getId());
            if (acc == null) {
                acc = new VentaPorFuncionario();
                acc.setId(item.getId());
                acc.setFuncionario(item.getFuncionario());
                acc.setTotal(0.0);
                acc.setCantidad(0L);
                acc.setProductoMasVendido(item.getProductoMasVendido());
                acc.setSucursales("");
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(item.getId(), acc);
            }

            acc.setTotal(acc.getTotal() + total);
            acc.setCantidad(acc.getCantidad() + cantidad);
            StringBuilder sucursales = new StringBuilder(
                    acc.getSucursales() != null ? acc.getSucursales() : "");
            fusionarSucursalesTexto(sucursales, item.getSucursales());
            acc.setSucursales(sucursales.toString());

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, (double) cantidad);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, (double) cantidad);
            }
        }
    }

    private void acumularFormaPago(
            Map<String, FormaPagoEstadistica> mapa,
            List<FormaPagoEstadistica> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (FormaPagoEstadistica item : items) {
            String key = item.getDescripcion() != null ? item.getDescripcion() : "";
            double total = item.getTotalMonto() != null
                    ? item.getTotalMonto().doubleValue()
                    : 0.0;
            long cantidad = item.getCantidadTransacciones() != null
                    ? item.getCantidadTransacciones()
                    : 0L;

            FormaPagoEstadistica acc = mapa.get(key);
            if (acc == null) {
                acc = new FormaPagoEstadistica();
                acc.setFormaPagoId(item.getFormaPagoId());
                acc.setDescripcion(key);
                acc.setTotalMonto(BigDecimal.ZERO);
                acc.setCantidadTransacciones(0L);
                acc.setPorcentaje(BigDecimal.ZERO);
                acc.setDesgloseMoneda(new ArrayList<>());
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(key, acc);
            }

            acc.setTotalMonto(acc.getTotalMonto().add(
                    item.getTotalMonto() != null ? item.getTotalMonto() : BigDecimal.ZERO));
            acc.setCantidadTransacciones(
                    acc.getCantidadTransacciones()
                            + (item.getCantidadTransacciones() != null ? item.getCantidadTransacciones() : 0L));
            combinarDesgloseMoneda(acc, item.getDesgloseMoneda());

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, (double) cantidad);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, (double) cantidad);
            }
        }
    }

    private void acumularGastosPorCategoria(
            Map<String, GastoPorCategoria> mapa,
            List<GastoPorCategoria> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (GastoPorCategoria item : items) {
            String key = item.getCategoria() != null ? item.getCategoria() : "Sin Categoría";
            double total = item.getTotal() != null ? item.getTotal() : 0.0;
            double cantidad = item.getCantidad() != null ? item.getCantidad().doubleValue() : 0.0;

            GastoPorCategoria acc = mapa.get(key);
            if (acc == null) {
                acc = new GastoPorCategoria();
                acc.setCategoria(key);
                acc.setTotal(0.0);
                acc.setCantidad(0L);
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(key, acc);
            }

            acc.setTotal(acc.getTotal() + total);
            acc.setCantidad(acc.getCantidad() + (item.getCantidad() != null ? item.getCantidad() : 0L));

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, cantidad);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, cantidad);
            }
        }
    }

    private void acumularVentasPorSucursal(
            Map<String, VentaPorSucursal> mapa,
            List<VentaPorSucursal> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (VentaPorSucursal item : items) {
            String key = String.valueOf(item.getSucId() != null ? item.getSucId() : item.getNombre());
            double total = item.getTotal() != null ? item.getTotal() : 0.0;

            VentaPorSucursal acc = mapa.get(key);
            if (acc == null) {
                acc = new VentaPorSucursal();
                acc.setSucId(item.getSucId());
                acc.setNombre(item.getNombre());
                acc.setTotal(0.0);
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(key, acc);
            }

            acc.setTotal(acc.getTotal() + total);

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, null);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, null);
            }
        }
    }

    private void acumularVentasPorCiudad(
            Map<String, VentaPorCiudad> mapa,
            List<VentaPorCiudad> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (VentaPorCiudad item : items) {
            String key = item.getCiudadId() != null
                    ? String.valueOf(item.getCiudadId())
                    : (item.getNombre() != null ? item.getNombre() : "sin-ciudad");
            double total = item.getTotal() != null ? item.getTotal() : 0.0;
            double cantidad = item.getCantidadVentas() != null ? item.getCantidadVentas() : 0.0;

            VentaPorCiudad acc = mapa.get(key);
            if (acc == null) {
                acc = new VentaPorCiudad();
                acc.setCiudadId(item.getCiudadId());
                acc.setNombre(item.getNombre());
                acc.setTotal(0.0);
                acc.setCantidadVentas(0.0);
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(key, acc);
            }

            acc.setTotal(acc.getTotal() + total);
            acc.setCantidadVentas(
                    (acc.getCantidadVentas() != null ? acc.getCantidadVentas() : 0.0) + cantidad);

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, cantidad);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, cantidad);
            }
        }
    }

    private void acumularProductos(
            Map<Long, ProductoVendidoEstadistica> mapa,
            List<ProductoVendidoEstadistica> items,
            String etiquetaPeriodo,
            boolean multiPeriodo,
            Integer anio) {
        for (ProductoVendidoEstadistica item : items) {
            if (item.getProductoId() == null) {
                continue;
            }
            double total = item.getTotalMonto() != null ? item.getTotalMonto() : 0.0;
            double cantidad = item.getCantidad() != null ? item.getCantidad() : 0.0;

            ProductoVendidoEstadistica acc = mapa.get(item.getProductoId());
            if (acc == null) {
                acc = new ProductoVendidoEstadistica();
                acc.setProductoId(item.getProductoId());
                acc.setDescripcion(item.getDescripcion());
                acc.setCantidad(0.0);
                acc.setTotalMonto(0.0);
                acc.setPorcentaje(0.0);
                acc.setCantidadEntrada(item.getCantidadEntrada());
                acc.setCantidadVentaMovimiento(item.getCantidadVentaMovimiento());
                acc.setIndiceRotacion(item.getIndiceRotacion());
                acc.setDesglosePeriodos(new ArrayList<>());
                acc.setDesgloseAnhos(new ArrayList<>());
                mapa.put(item.getProductoId(), acc);
            }

            acc.setCantidad(acc.getCantidad() + cantidad);
            acc.setTotalMonto(acc.getTotalMonto() + total);
            acc.setCantidadEntrada(sumarNullable(acc.getCantidadEntrada(), item.getCantidadEntrada()));
            acc.setCantidadVentaMovimiento(
                    sumarNullable(acc.getCantidadVentaMovimiento(), item.getCantidadVentaMovimiento()));

            if (multiPeriodo) {
                agregarDesglose(acc.getDesglosePeriodos(), etiquetaPeriodo, total, cantidad);
                agregarDesgloseAnho(acc.getDesgloseAnhos(), anio, total, cantidad);
            }
        }
    }

    private void combinarDesgloseMoneda(
            FormaPagoEstadistica destino,
            List<FormaPagoMonedaDesglose> origen) {
        if (origen == null || origen.isEmpty()) {
            return;
        }
        if (destino.getDesgloseMoneda() == null) {
            destino.setDesgloseMoneda(new ArrayList<>());
        }
        for (FormaPagoMonedaDesglose item : origen) {
            FormaPagoMonedaDesglose existente = destino.getDesgloseMoneda().stream()
                    .filter(d -> d.getMonedaId() != null && d.getMonedaId().equals(item.getMonedaId()))
                    .findFirst()
                    .orElse(null);
            if (existente != null) {
                existente.setTotalMonto(existente.getTotalMonto().add(
                        item.getTotalMonto() != null ? item.getTotalMonto() : BigDecimal.ZERO));
                existente.setCantidadTransacciones(
                        existente.getCantidadTransacciones()
                                + (item.getCantidadTransacciones() != null ? item.getCantidadTransacciones() : 0L));
            } else {
                destino.getDesgloseMoneda().add(new FormaPagoMonedaDesglose(
                        item.getMonedaId(),
                        item.getDenominacion(),
                        item.getSimbolo(),
                        item.getCantidadTransacciones(),
                        item.getTotalMonto()));
            }
        }
    }

    private void recalcularPorcentajeFormaPago(Iterable<FormaPagoEstadistica> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (FormaPagoEstadistica item : items) {
            total = total.add(item.getTotalMonto() != null ? item.getTotalMonto() : BigDecimal.ZERO);
        }
        for (FormaPagoEstadistica item : items) {
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                item.setPorcentaje(item.getTotalMonto()
                        .multiply(new BigDecimal("100"))
                        .divide(total, 2, RoundingMode.HALF_UP));
            } else {
                item.setPorcentaje(BigDecimal.ZERO);
            }
        }
    }

    private void recalcularPorcentajeProductos(Iterable<ProductoVendidoEstadistica> items) {
        double total = 0.0;
        for (ProductoVendidoEstadistica item : items) {
            total += item.getTotalMonto() != null ? item.getTotalMonto() : 0.0;
        }
        for (ProductoVendidoEstadistica item : items) {
            item.setPorcentaje(total > 0 ? (item.getTotalMonto() / total) * 100.0 : 0.0);
            item.setIndiceRotacion(calcularIndiceRotacion(item));
        }
    }

    private Double calcularIndiceRotacion(ProductoVendidoEstadistica item) {
        Double entrada = item.getCantidadEntrada();
        Double ventaMov = item.getCantidadVentaMovimiento();
        if (entrada != null && entrada > 0 && ventaMov != null) {
            return ventaMov / entrada;
        }
        return item.getIndiceRotacion();
    }

    private Double sumarNullable(Double a, Double b) {
        if (a == null && b == null) {
            return null;
        }
        return (a != null ? a : 0.0) + (b != null ? b : 0.0);
    }

    private List<VentaPorFuncionario> ordenarPorTotalDesc(Iterable<VentaPorFuncionario> items) {
        List<VentaPorFuncionario> lista = new ArrayList<>();
        items.forEach(lista::add);
        lista.sort(Comparator.comparing(
                (VentaPorFuncionario v) -> v.getTotal() != null ? v.getTotal() : 0.0).reversed());
        return lista;
    }

    private List<VentaPorSucursal> ordenarVentasPorSucursalDesc(Iterable<VentaPorSucursal> items) {
        List<VentaPorSucursal> lista = new ArrayList<>();
        items.forEach(lista::add);
        lista.sort(Comparator.comparing(
                (VentaPorSucursal v) -> v.getTotal() != null ? v.getTotal() : 0.0).reversed());
        return lista;
    }

    private List<VentaPorCiudad> ordenarVentasPorCiudadDesc(Iterable<VentaPorCiudad> items) {
        List<VentaPorCiudad> lista = new ArrayList<>();
        items.forEach(lista::add);
        lista.sort(Comparator.comparing(
                (VentaPorCiudad v) -> v.getTotal() != null ? v.getTotal() : 0.0).reversed());
        return lista;
    }

    private List<ProductoVendidoEstadistica> ordenarProductos(
            Iterable<ProductoVendidoEstadistica> items,
            Boolean ascendente,
            int limit) {
        List<ProductoVendidoEstadistica> lista = new ArrayList<>();
        items.forEach(lista::add);
        Comparator<ProductoVendidoEstadistica> cmp = Comparator.comparing(
                (ProductoVendidoEstadistica p) -> p.getCantidad() != null ? p.getCantidad() : 0.0);
        if (!Boolean.TRUE.equals(ascendente)) {
            cmp = cmp.reversed();
        }
        lista.sort(cmp);
        if (lista.size() > limit) {
            return new ArrayList<>(lista.subList(0, limit));
        }
        return lista;
    }

    private String resolverNombreSucursal(Long sucId) {
        if (sucId == null) {
            return "Todas";
        }
        return sucursalService.findById(sucId)
                .map(s -> s.getNombre())
                .orElse("Sucursal " + sucId);
    }

    private void validarPeriodos(List<PeriodoGraficoInput> periodos) {
        if (periodos == null || periodos.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un período");
        }
    }

    private String extraerFecha(String inicio) {
        if (inicio == null || inicio.isBlank()) {
            throw new IllegalArgumentException("Fecha de inicio requerida");
        }
        return inicio.length() >= 10 ? inicio.substring(0, 10) : inicio;
    }
}
