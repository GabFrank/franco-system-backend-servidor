package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.graphql.financiero.dto.FormaPagoMonedaDesglose;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class CobroDetalleService extends CrudService<CobroDetalle, CobroDetalleRepository, EmbebedPrimaryKey> {
    private final CobroDetalleRepository repository;

    @Override
    public CobroDetalleRepository getRepository() {
        return repository;
    }

    @Autowired
    MovimientoStockService movimientoStockService;

    public List<CobroDetalle> findByCobroId(Long id, Long sucId) {
        return repository.findByCobroIdAndSucursalId(id, sucId);
    }

    public List<CobroDetalle> findByCajaId(Long id, Long sucId) {
        return repository.findByCajaId(id, sucId);
    }

    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPago() {
        return transformarResultadosAEstadisticas(
                repository.obtenerEstadisticasFormaPago(),
                repository.obtenerDesgloseMonedaFormaPago());
    }

    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorSucursal(Long sucursalId) {
        return transformarResultadosAEstadisticas(
                repository.obtenerEstadisticasFormaPagoPorSucursal(sucursalId),
                repository.obtenerDesgloseMonedaFormaPagoPorSucursal(sucursalId));
    }

    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorFecha(LocalDateTime inicio, LocalDateTime fin) {
        return transformarResultadosAEstadisticas(
                repository.obtenerEstadisticasFormaPagoPorFecha(inicio, fin),
                repository.obtenerDesgloseMonedaFormaPagoPorFecha(inicio, fin));
    }

    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorFechaYSucursal(
            LocalDateTime inicio, LocalDateTime fin, Long sucursalId) {
        return transformarResultadosAEstadisticas(
                repository.obtenerEstadisticasFormaPagoPorFechaYSucursal(inicio, fin, sucursalId),
                repository.obtenerDesgloseMonedaFormaPagoPorFechaYSucursal(inicio, fin, sucursalId));
    }

    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoConFiltros(
            LocalDateTime inicio, LocalDateTime fin, Long sucursalId) {

        boolean tieneFechas = inicio != null && fin != null;
        boolean tieneSucursal = sucursalId != null && sucursalId > 0;

        if (tieneFechas && tieneSucursal) {
            return obtenerEstadisticasFormaPagoPorFechaYSucursal(inicio, fin, sucursalId);
        } else if (tieneFechas) {
            return obtenerEstadisticasFormaPagoPorFecha(inicio, fin);
        } else if (tieneSucursal) {
            return obtenerEstadisticasFormaPagoPorSucursal(sucursalId);
        } else {
            return obtenerEstadisticasFormaPago();
        }
    }

    private List<FormaPagoEstadistica> transformarResultadosAEstadisticas(
            List<Object[]> resultados,
            List<Object[]> desgloseMoneda) {
        List<FormaPagoEstadistica> estadisticas = new ArrayList<>();
        BigDecimal montoTotal = BigDecimal.ZERO;
        Map<Long, List<FormaPagoMonedaDesglose>> desglosePorFormaPago = agruparDesgloseMoneda(desgloseMoneda);

        for (Object[] fila : resultados) {
            BigDecimal monto = fila[3] != null ? new BigDecimal(fila[3].toString()) : BigDecimal.ZERO;
            montoTotal = montoTotal.add(monto);
        }

        for (Object[] fila : resultados) {
            Long formaPagoId = fila[0] != null ? Long.parseLong(fila[0].toString()) : null;
            String descripcion = fila[1] != null ? fila[1].toString() : "";
            Long cantidadTransacciones = fila[2] != null ? Long.parseLong(fila[2].toString()) : 0L;
            BigDecimal totalMonto = fila[3] != null ? new BigDecimal(fila[3].toString()) : BigDecimal.ZERO;

            BigDecimal porcentaje = BigDecimal.ZERO;
            if (montoTotal.compareTo(BigDecimal.ZERO) > 0) {
                porcentaje = totalMonto.multiply(new BigDecimal("100"))
                        .divide(montoTotal, 2, RoundingMode.HALF_UP);
            }

            FormaPagoEstadistica estadistica = new FormaPagoEstadistica();
            estadistica.setFormaPagoId(formaPagoId);
            estadistica.setDescripcion(descripcion);
            estadistica.setCantidadTransacciones(cantidadTransacciones);
            estadistica.setTotalMonto(totalMonto);
            estadistica.setPorcentaje(porcentaje);
            if (formaPagoId != null) {
                estadistica.setDesgloseMoneda(
                        desglosePorFormaPago.getOrDefault(formaPagoId, new ArrayList<>()));
            }
            estadisticas.add(estadistica);
        }

        return estadisticas;
    }

    private Map<Long, List<FormaPagoMonedaDesglose>> agruparDesgloseMoneda(List<Object[]> desgloseMoneda) {
        Map<Long, List<FormaPagoMonedaDesglose>> mapa = new HashMap<>();
        if (desgloseMoneda == null) {
            return mapa;
        }

        for (Object[] fila : desgloseMoneda) {
            Long formaPagoId = fila[0] != null ? Long.parseLong(fila[0].toString()) : null;
            if (formaPagoId == null) {
                continue;
            }

            Long monedaId = fila[1] != null ? Long.parseLong(fila[1].toString()) : null;
            String denominacion = fila[2] != null ? fila[2].toString() : "";
            String simbolo = fila[3] != null ? fila[3].toString() : "";
            Long cantidadTransacciones = fila[4] != null ? Long.parseLong(fila[4].toString()) : 0L;
            BigDecimal totalMonto = fila[5] != null ? new BigDecimal(fila[5].toString()) : BigDecimal.ZERO;

            mapa.computeIfAbsent(formaPagoId, k -> new ArrayList<>()).add(
                    new FormaPagoMonedaDesglose(
                            monedaId,
                            denominacion,
                            simbolo,
                            cantidadTransacciones,
                            totalMonto));
        }

        return mapa;
    }

    @Override
    public CobroDetalle save(CobroDetalle entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        CobroDetalle e = super.save(entity);
        return e;
    }
}
