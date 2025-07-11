package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecepcionMercaderiaItemRepository extends HelperRepository<RecepcionMercaderiaItem, Long> {
    
    default Class<RecepcionMercaderiaItem> getEntityClass() {
        return RecepcionMercaderiaItem.class;
    }

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.recepcionMercaderia.id = :recepcionId")
    List<RecepcionMercaderiaItem> findByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemId(@Param("notaRecepcionItemId") Long notaRecepcionItemId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.producto.id = :productoId AND rmi.sucursalEntrega.id = :sucursalId")
    List<RecepcionMercaderiaItem> findByProductoIdAndSucursalEntregaId(@Param("productoId") Long productoId, @Param("sucursalId") Long sucursalId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.lote = :lote AND rmi.producto.id = :productoId")
    List<RecepcionMercaderiaItem> findByLoteAndProductoId(@Param("lote") String lote, @Param("productoId") Long productoId);
} 