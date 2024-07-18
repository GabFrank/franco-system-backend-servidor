package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import com.franco.dev.repository.operaciones.VentaItemRepository;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.CostosPorProductoService;
import com.franco.dev.service.productos.ProductoService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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


//    public List<VentaItem> findByAll(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByProveedor(texto.toLowerCase());
//
    public List<VentaItem> findByVentaId(Long id, Long sucId){
        return repository.findByVentaIdAndSucursalId(id, sucId);
    }

    @Override
    public VentaItem save(VentaItem entity) {
        if(entity.getPrecioCosto()==null){
            CostoPorProducto costoPorProducto = costosPorProductoService.findLastByProductoId(entity.getProducto().getId()).orElse(null);
            if(costoPorProducto!=null){
                entity.setPrecioCosto(costoPorProducto.getUltimoPrecioCompra());
            }
        }
        VentaItem e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}