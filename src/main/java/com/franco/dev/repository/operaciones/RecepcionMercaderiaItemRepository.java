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

    /**
     * Busca RecepcionMercaderiaItem por notaRecepcionItemId y sucursalId
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId " +
           "AND rmi.sucursalEntrega.id = :sucursalId")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemIdAndSucursalId(
        @Param("notaRecepcionItemId") Long notaRecepcionItemId, 
        @Param("sucursalId") Long sucursalId);

    /**
     * Cuenta cuántos RecepcionMercaderiaItem tiene una RecepcionMercaderia
     */
    @Query("SELECT COUNT(rmi) FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.recepcionMercaderia.id = :recepcionId")
    Long countByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);

    /**
     * Busca RecepcionMercaderiaItem rechazados por notaRecepcionItemId y sucursalId
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId " +
           "AND rmi.sucursalEntrega.id = :sucursalId " +
           "AND rmi.cantidadRechazada > 0")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemIdAndSucursalIdAndRechazados(
        @Param("notaRecepcionItemId") Long notaRecepcionItemId, 
        @Param("sucursalId") Long sucursalId);

    /**
     * Busca RecepcionMercaderiaItem por recepcionMercaderiaId y sucursales
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
           "AND rmi.sucursalEntrega.id IN :sucursalesIds")
    List<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdAndSucursales(
        @Param("recepcionId") Long recepcionId,
        @Param("sucursalesIds") List<Long> sucursalesIds);
} 