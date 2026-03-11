package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DevolucionItemRepository extends HelperRepository<DevolucionItem, Long> {
    
    default Class<DevolucionItem> getEntityClass() {
        return DevolucionItem.class;
    }

    @Query("SELECT di FROM DevolucionItem di WHERE di.devolucion.id = :devolucionId")
    List<DevolucionItem> findByDevolucionId(@Param("devolucionId") Long devolucionId);

    @Query("SELECT di FROM DevolucionItem di WHERE di.producto.id = :productoId")
    List<DevolucionItem> findByProductoId(@Param("productoId") Long productoId);

    @Query("SELECT di FROM DevolucionItem di WHERE di.recepcionMercaderiaItem.id = :recepcionItemId")
    List<DevolucionItem> findByRecepcionMercaderiaItemId(@Param("recepcionItemId") Long recepcionItemId);
} 