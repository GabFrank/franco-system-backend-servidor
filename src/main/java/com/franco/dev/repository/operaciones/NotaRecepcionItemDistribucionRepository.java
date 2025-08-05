package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.franco.dev.domain.empresarial.Sucursal;

public interface NotaRecepcionItemDistribucionRepository extends HelperRepository<NotaRecepcionItemDistribucion, Long> {
    
    default Class<NotaRecepcionItemDistribucion> getEntityClass() {
        return NotaRecepcionItemDistribucion.class;
    }

    /**
     * Buscar distribuciones por NotaRecepcionItem
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId")
    List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemId(@Param("notaRecepcionItemId") Long notaRecepcionItemId);

    /**
     * Buscar distribuciones por sucursal de entrega
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.sucursalEntrega.id = :sucursalId")
    List<NotaRecepcionItemDistribucion> findBySucursalEntregaId(@Param("sucursalId") Long sucursalId);

    /**
     * Buscar distribuciones por NotaRecepcion (a través de NotaRecepcionItem)
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.notaRecepcion.id = :notaRecepcionId")
    List<NotaRecepcionItemDistribucion> findByNotaRecepcionId(@Param("notaRecepcionId") Long notaRecepcionId);

    /**
     * Obtener cantidad total distribuida para un NotaRecepcionItem específico
     */
    @Query("SELECT COALESCE(SUM(nrid.cantidad), 0.0) FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId")
    Double getTotalDistributedQuantityByNotaRecepcionItemId(@Param("notaRecepcionItemId") Long notaRecepcionItemId);

    /**
     * Obtener cantidad distribuida para un NotaRecepcionItem en una sucursal específica
     */
    @Query("SELECT COALESCE(SUM(nrid.cantidad), 0.0) FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId AND nrid.sucursalEntrega.id = :sucursalId")
    Double getDistributedQuantityByNotaRecepcionItemIdAndSucursalId(@Param("notaRecepcionItemId") Long notaRecepcionItemId, @Param("sucursalId") Long sucursalId);

    /**
     * Eliminar todas las distribuciones de un NotaRecepcionItem
     */
    @Modifying
    @Query("DELETE FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId")
    void deleteByNotaRecepcionItemId(@Param("notaRecepcionItemId") Long notaRecepcionItemId);

    /**
     * Buscar distribuciones por NotaRecepcionItem y sucursal específica
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId AND nrid.sucursalEntrega.id = :sucursalId")
    List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemIdAndSucursalEntregaId(@Param("notaRecepcionItemId") Long notaRecepcionItemId, @Param("sucursalId") Long sucursalId);

    /**
     * Buscar distribuciones por sucursal de influencia
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.sucursalInfluencia.id = :sucursalId")
    List<NotaRecepcionItemDistribucion> findBySucursalInfluenciaId(@Param("sucursalId") Long sucursalId);

    /**
     * Buscar distribuciones por NotaRecepcionItem y sucursal de influencia específica
     */
    @Query("SELECT nrid FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId AND nrid.sucursalInfluencia.id = :sucursalId")
    List<NotaRecepcionItemDistribucion> findByNotaRecepcionItemIdAndSucursalInfluenciaId(@Param("notaRecepcionItemId") Long notaRecepcionItemId, @Param("sucursalId") Long sucursalId);

    /**
     * Obtener cantidad distribuida para un NotaRecepcionItem en una sucursal de influencia específica
     */
    @Query("SELECT COALESCE(SUM(nrid.cantidad), 0.0) FROM NotaRecepcionItemDistribucion nrid WHERE nrid.notaRecepcionItem.id = :notaRecepcionItemId AND nrid.sucursalInfluencia.id = :sucursalId")
    Double getDistributedQuantityByNotaRecepcionItemIdAndSucursalInfluenciaId(@Param("notaRecepcionItemId") Long notaRecepcionItemId, @Param("sucursalId") Long sucursalId);

    /**
     * Buscar distribuciones por pedido ID (a través de NotaRecepcionItem -> NotaRecepcion -> Pedido)
     * con join fetch para cargar las relaciones lazy
     */
    @Query("SELECT DISTINCT nrid FROM NotaRecepcionItemDistribucion nrid " +
           "JOIN FETCH nrid.sucursalEntrega " +
           "JOIN FETCH nrid.notaRecepcionItem nri " +
           "JOIN FETCH nri.notaRecepcion nr " +
           "WHERE nr.pedido.id = :pedidoId")
    List<NotaRecepcionItemDistribucion> findByPedidoId(@Param("pedidoId") Long pedidoId);

    /**
     * Obtener sucursales únicas de entrega por pedido ID
     */
    @Query("SELECT DISTINCT nrid.sucursalEntrega FROM NotaRecepcionItemDistribucion nrid " +
           "WHERE nrid.notaRecepcionItem.notaRecepcion.pedido.id = :pedidoId " +
           "ORDER BY nrid.sucursalEntrega.nombre")
    List<Sucursal> findSucursalesUnicasByPedidoId(@Param("pedidoId") Long pedidoId);

} 