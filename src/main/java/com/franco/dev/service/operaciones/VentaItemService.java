package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.graphql.operaciones.dto.ProductoVendidoEstadistica;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.ProductoService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class VentaItemService extends CrudService<VentaItem, VentaItemRepository, EmbebedPrimaryKey> {
    private final VentaItemRepository repository;

    @Autowired
    private CostosPorProductoService costosPorProductoService;

    @Override
    public VentaItemRepository getRepository() {
        return repository;
    }

    @Autowired
    MovimientoStockService movimientoStockService;

    // public List<VentaItem> findByAll(String texto){
    // texto = texto.replace(' ', '%');
    // return repository.findByProveedor(texto.toLowerCase());
    //
    public List<VentaItem> findByVentaId(Long id, Long sucId) {
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaItem save(VentaItem entity) {
        if (entity.getPrecioCosto() == null) {
            CostoPorProducto costoPorProducto = costosPorProductoService
                    .findLastByProductoId(entity.getProducto().getId());
            if (costoPorProducto != null) {
                entity.setPrecioCosto(costoPorProducto.getUltimoPrecioCompra());
            }
        }
        VentaItem e = super.save(entity);
        // personaPublisher.publish(p);
        return e;
    }

    public VentaItem findByIdAndSucursalId(Long id, Long sucId) {
        return repository.findByIdAndSucursalId(id, sucId);
    }

    public Boolean deleteByIdAndSucursalId(Long id, Long sucId) {
        return repository.deleteByIdAndSucursalId(id, sucId);
    }

    /**
     * Obtiene estadísticas de productos más vendidos con filtros
     */
    public List<ProductoVendidoEstadistica> obtenerProductosMasVendidos(LocalDateTime inicio, LocalDateTime fin,
            Integer limit,
            Long sucursalId) {
        List<Object[]> resultados;
        if (sucursalId != null && sucursalId > 0) {
            resultados = repository.obtenerProductosMasVendidosPorSucursal(inicio, fin, limit, sucursalId);
        } else {
            resultados = repository.obtenerProductosMasVendidos(inicio, fin, limit);
        }
        return transformarResultadosAEstadisticas(resultados);
    }

    /**
     * Transforma los resultados de la query nativa a objetos
     * ProductoVendidoEstadistica
     */
    private List<ProductoVendidoEstadistica> transformarResultadosAEstadisticas(List<Object[]> resultados) {
        List<ProductoVendidoEstadistica> estadisticas = new ArrayList<>();
        Double montoTotalGeneral = 0.0;

        // Calcular monto total para porcentajes
        for (Object[] fila : resultados) {
            Double monto = fila[3] != null ? Double.parseDouble(fila[3].toString()) : 0.0;
            montoTotalGeneral += monto;
        }

        // Transformar cada fila
        for (Object[] fila : resultados) {
            Long productoId = fila[0] != null ? Long.parseLong(fila[0].toString()) : null;
            String descripcion = fila[1] != null ? fila[1].toString() : "";
            Double cantidad = fila[2] != null ? Double.parseDouble(fila[2].toString()) : 0.0;
            Double totalMonto = fila[3] != null ? Double.parseDouble(fila[3].toString()) : 0.0;

            Double porcentaje = 0.0;
            if (montoTotalGeneral > 0) {
                porcentaje = (totalMonto * 100.0) / montoTotalGeneral;
                // Redondear a 2 decimales
                porcentaje = Math.round(porcentaje * 100.0) / 100.0;
            }

            estadisticas.add(new ProductoVendidoEstadistica(
                    productoId,
                    descripcion,
                    cantidad,
                    totalMonto,
                    porcentaje));
        }

        return estadisticas;
    }
}