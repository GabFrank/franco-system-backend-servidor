package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class CostosPorProductoService extends CrudService<CostoPorProducto, CostosPorProductoRepository, Long> {

    @Autowired
    private final CostosPorProductoRepository repository;

    @Autowired
    private MovimientoStockService movimientoStockService;
    @Autowired
    private com.franco.dev.service.configuraciones.ModificacionService modificacionService;


    @Override
    public CostosPorProductoRepository getRepository() {
        return repository;
    }

    public CostoPorProducto findLastByProductoId(Long prdoId){
        List<CostoPorProducto> c = repository.findLastByProductoId(prdoId, PageRequest.of(0,1));
        return c.size() > 0 ? c.get(0) : null;
    }

    public Page<CostoPorProducto> findByProductoId(Long id, Pageable page){
        return repository.findByProductoId(id, page);
    }

    public CostoPorProducto findByMovimientoStockId(Long id) {
        return repository.findByMovimientoStockId(id);
    }

    @Override
    public CostoPorProducto save(CostoPorProducto entity) {
        if(entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        
        CostoPorProducto entidadAnterior = null;
        boolean esNuevo = (entity.getId() == null);
        if (!esNuevo) {
            java.util.Optional<CostoPorProducto> costoOpt = repository.findById(entity.getId());
            if (costoOpt != null && costoOpt.isPresent()) {
                CostoPorProducto original = costoOpt.get();
                entidadAnterior = new CostoPorProducto();
                entidadAnterior.setId(original.getId());
                entidadAnterior.setCostoMedio(original.getCostoMedio());
                entidadAnterior.setUltimoPrecioCompra(original.getUltimoPrecioCompra());
                entidadAnterior.setUltimoPrecioVenta(original.getUltimoPrecioVenta());
                entidadAnterior.setExistencia(original.getExistencia());
                entidadAnterior.setCotizacion(original.getCotizacion());
                entidadAnterior.setCreadoEn(original.getCreadoEn());
                entidadAnterior.setProducto(original.getProducto());
                entidadAnterior.setSucursal(original.getSucursal());
                entidadAnterior.setMoneda(original.getMoneda());
                entidadAnterior.setMovimientoStock(original.getMovimientoStock());
                entidadAnterior.setUsuario(original.getUsuario());
            }
        }
        
        CostoPorProducto e = super.save(entity);
        repository.flush();
        
        try {
            if (esNuevo) {
                modificacionService.registrarInsercion(e, "COSTO_POR_PRODUCTO", "productos", "costo_por_producto");
            } else if (entidadAnterior != null) {
                modificacionService.registrarActualizacion(entidadAnterior, e, "COSTO_POR_PRODUCTO", "productos", "costo_por_producto");
            }
        } catch (Exception ex) {
            System.err.println("Error registrando modificación de costo por producto: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        return e;
    }

    @Override
    @javax.transaction.Transactional
    public Boolean deleteById(Long id) {
        try {
            CostoPorProducto entidad = repository.findById(id).orElse(null);
            if (entidad != null) {
                Boolean resultado = super.deleteById(id);
                try {
                    modificacionService.registrarEliminacion(entidad, "COSTO_POR_PRODUCTO", "productos", "costo_por_producto");
                } catch (Exception ex) {
                    System.err.println("Error registrando eliminación de costo por producto: " + ex.getMessage());
                }
                return resultado;
            }
            return super.deleteById(id);
        } catch (Exception e) {
            return false;
        }
    }

    public Double calcularCostoMedio(Long productoId, Double cantidad, Double precioCompra){
        CostoPorProducto costo = findLastByProductoId(productoId);
        if(costo != null){
            Double ultimoCostoMedio = costo.getCostoMedio();
            Double stockActual = movimientoStockService.stockByProductoId(productoId);
            if(ultimoCostoMedio != null && stockActual != null && stockActual > 0){
                return ((ultimoCostoMedio * stockActual) + (cantidad * precioCompra)) / (stockActual + cantidad);
            } else {
                return precioCompra;
            }
        } else {
            return precioCompra;
        }
    }
}
