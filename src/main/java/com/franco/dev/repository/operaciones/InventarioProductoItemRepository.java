package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventarioProductoItemRepository extends HelperRepository<InventarioProductoItem, Long> {

    default Class<InventarioProductoItem> getEntityClass() {
        return InventarioProductoItem.class;
    }

    public List<InventarioProductoItem> findByInventarioProductoIdOrderByIdDesc(Long id, Pageable pageable);
    
}