package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.graphql.financiero.dto.FormaPagoEstadistica;
import com.franco.dev.repository.operaciones.CobroDetalleRepository;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // public List<CobroDetalle> findByAll(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByProveedor(texto.toLowerCase());
    //
    public List<CobroDetalle> findByCobroId(Long id, Long sucId) {
        return repository.findByCobroIdAndSucursalId(id, sucId);
    }

    public List<CobroDetalle> findByCajaId(Long id, Long sucId) {
        return repository.findByCajaId(id, sucId);
    }

    /**
     * Obtiene estadísticas de formas de pago para todas las sucursales
     */
    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPago() {
        List<Object[]> resultados = repository.obtenerEstadisticasFormaPago();
        return transformarResultadosAEstadisticas(resultados);
    }

    /**
     * Obtiene estadísticas de formas de pago para una sucursal específica
     */
    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorSucursal(Long sucursalId) {
        List<Object[]> resultados = repository.obtenerEstadisticasFormaPagoPorSucursal(sucursalId);
        return transformarResultadosAEstadisticas(resultados);
    }

    /**
     * Obtiene estadísticas de formas de pago por rango de fechas (todas las
     * sucursales)
     */
    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorFecha(LocalDateTime inicio, LocalDateTime fin) {
        List<Object[]> resultados = repository.obtenerEstadisticasFormaPagoPorFecha(inicio, fin);
        return transformarResultadosAEstadisticas(resultados);
    }

    /**
     * Obtiene estadísticas de formas de pago por rango de fechas y sucursal
     * específica
     */
    public List<FormaPagoEstadistica> obtenerEstadisticasFormaPagoPorFechaYSucursal(
            LocalDateTime inicio, LocalDateTime fin, Long sucursalId) {
        List<Object[]> resultados = repository.obtenerEstadisticasFormaPagoPorFechaYSucursal(inicio, fin, sucursalId);
        return transformarResultadosAEstadisticas(resultados);
    }

    /**
     * Método principal que maneja todos los escenarios de filtros
     */
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

    /**
     * Transforma los resultados de la query nativa a objetos FormaPagoEstadistica
     */
    private List<FormaPagoEstadistica> transformarResultadosAEstadisticas(List<Object[]> resultados) {
        List<FormaPagoEstadistica> estadisticas = new ArrayList<>();
        BigDecimal montoTotal = BigDecimal.ZERO;

        // Calcular monto total para porcentajes
        for (Object[] fila : resultados) {
            BigDecimal monto = fila[3] != null ? new BigDecimal(fila[3].toString()) : BigDecimal.ZERO;
            montoTotal = montoTotal.add(monto);
        }

        // Transformar cada fila
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

            estadisticas.add(new FormaPagoEstadistica(
                    formaPagoId,
                    descripcion,
                    cantidadTransacciones,
                    totalMonto,
                    porcentaje));
        }

        return estadisticas;
    }

    @Override
    public CobroDetalle save(CobroDetalle entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        CobroDetalle e = super.save(entity);
        // personaPublisher.publish(p);
        return e;
    }
}