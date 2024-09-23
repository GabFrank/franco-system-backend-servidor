package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.CostoPorProducto;
import com.franco.dev.repository.productos.CostosPorProductoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class CostosPorProductoService extends CrudService<CostoPorProducto, CostosPorProductoRepository, Long> {

    @Autowired
    private final CostosPorProductoRepository repository;
//    private final PersonaPublisher personaPublisher;


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
        return super.save(entity);
    }
}
