package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class InventarioProductoItemService extends CrudService<InventarioProductoItem, InventarioProductoItemRepository, Long> {
    private final InventarioProductoItemRepository repository;

    @Override
    public InventarioProductoItemRepository getRepository() {
        return repository;
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id, Pageable pageable) {
        return repository.findByInventarioProductoIdOrderByIdDesc(id, pageable);
    }

    public List<InventarioProductoItem> findByInventarioProductoId(Long id) {
        return repository.findByInventarioProductoId(id);
    }

    public Page<InventarioProductoItem> findAllWithFilters(
            List<Long> sucursalIdList,
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<Long> usuarioIdList,
            List<Long> productoIdList,
            Pageable pageable) {
        return repository.findAllWithFilters(
                sucursalIdList,
                startDate,
                endDate,
                usuarioIdList,
                productoIdList,
                pageable
        );
    }

    @Override
    public InventarioProductoItem save(InventarioProductoItem entity) {
        InventarioProductoItem e = new InventarioProductoItem();
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        e = super.save(entity);
        return e;
    }
}