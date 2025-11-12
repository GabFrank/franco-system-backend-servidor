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

    public List<InventarioProductoItem> findByInventarioIdAndProductoId(Long invId, Long proId){
        return repository.findByInventarioIdAndProductoId(invId, proId);
    }

    public Page<InventarioProductoItem> findItemsParaRevisar(Long inventarioId, String filtro, Pageable pageable) {
        return repository.findItemsParaRevisar(inventarioId, filtro, pageable);
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
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());

        Long inventarioId = null;
        Long productoId = null;
        if (entity.getInventarioProducto() != null && entity.getInventarioProducto().getInventario() != null) {
            inventarioId = entity.getInventarioProducto().getInventario().getId();
        }
        if (entity.getPresentacion() != null && entity.getPresentacion().getProducto() != null) {
            productoId = entity.getPresentacion().getProducto().getId();
        }

        if (inventarioId != null && productoId != null) {
            List<InventarioProductoItem> existingItems = findByInventarioIdAndProductoId(inventarioId, productoId);
            if (!existingItems.isEmpty()) {
                throw new IllegalStateException("El producto ya fue registrado en este inventario");
            }
        }

        return super.save(entity);
    }
}