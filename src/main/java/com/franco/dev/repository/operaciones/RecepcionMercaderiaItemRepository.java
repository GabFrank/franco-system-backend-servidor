package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.franco.dev.domain.operaciones.EstadoVerificacion;
import com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO;

import java.util.List;

public interface RecepcionMercaderiaItemRepository extends HelperRepository<RecepcionMercaderiaItem, Long> {

    default Class<RecepcionMercaderiaItem> getEntityClass() {
        return RecepcionMercaderiaItem.class;
    }

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.recepcionMercaderia.id = :recepcionId")
    List<RecepcionMercaderiaItem> findByRecepcionMercaderiaId(@Param("recepcionId") Long recepcionId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemId(@Param("notaRecepcionItemId") Long notaRecepcionItemId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.notaRecepcionItemDistribucion.id = :distribucionId")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemDistribucionId(@Param("distribucionId") Long distribucionId);

    /**
     * Busca RecepcionMercaderiaItem por notaRecepcionItemId y múltiples sucursales
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
            "WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId " +
            "AND rmi.sucursalEntrega.id IN :sucursalesIds")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemIdAndSucursalesIds(
            @Param("notaRecepcionItemId") Long notaRecepcionItemId,
            @Param("sucursalesIds") List<Long> sucursalesIds);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.producto.id = :productoId AND rmi.sucursalEntrega.id = :sucursalId")
    List<RecepcionMercaderiaItem> findByProductoIdAndSucursalEntregaId(@Param("productoId") Long productoId,
            @Param("sucursalId") Long sucursalId);

    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi WHERE rmi.lote = :lote AND rmi.producto.id = :productoId")
    List<RecepcionMercaderiaItem> findByLoteAndProductoId(@Param("lote") String lote,
            @Param("productoId") Long productoId);

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

    /**
     * Busca RecepcionMercaderiaItem por recepcionMercaderiaId con paginación y
     * filtros (sin filtro de estado)
     */
    @Query(value = "SELECT DISTINCT rmi " +
             "FROM RecepcionMercaderiaItem rmi " +
             "JOIN rmi.notaRecepcionItem nri " +
             "JOIN nri.producto p " +
             "LEFT JOIN rmi.notaRecepcionItemDistribucion nrid " +
             "LEFT JOIN nrid.sucursalEntrega s " +
             "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
             "AND ( :filtroTexto IS NULL OR :filtroTexto = '' OR " +
             "      LOWER(p.descripcion) LIKE CONCAT('%', LOWER(:filtroTexto), '%') OR " +
             "      str(p.id) LIKE CONCAT('%', :filtroTexto, '%') " +
             ") " +
             "ORDER BY rmi.id ASC", countQuery = "SELECT COUNT(DISTINCT rmi) " +
                     "FROM RecepcionMercaderiaItem rmi " +
                     "JOIN rmi.notaRecepcionItem nri " +
                     "JOIN nri.producto p " +
                     "LEFT JOIN rmi.notaRecepcionItemDistribucion nrid " +
                     "LEFT JOIN nrid.sucursalEntrega s " +
                     "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
                     "AND ( :filtroTexto IS NULL OR :filtroTexto = '' OR " +
                     "      LOWER(p.descripcion) LIKE CONCAT('%', LOWER(:filtroTexto), '%') OR " +
                     "      str(p.id) LIKE CONCAT('%', :filtroTexto, '%') " +
                     ") ")
    Page<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdPaginados(
             @Param("recepcionId") Long recepcionId,
             @Param("filtroTexto") String filtroTexto,
             Pageable pageable);

    /**
     * Busca RecepcionMercaderiaItem por recepcionMercaderiaId con paginación,
     * filtros y estado específico
     */
    @Query(value = "SELECT DISTINCT rmi " +
             "FROM RecepcionMercaderiaItem rmi " +
             "JOIN rmi.notaRecepcionItem nri " +
             "JOIN nri.producto p " +
             "LEFT JOIN rmi.notaRecepcionItemDistribucion nrid " +
             "LEFT JOIN nrid.sucursalEntrega s " +
             "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
             "AND ( :filtroTexto IS NULL OR :filtroTexto = '' OR " +
             "      LOWER(p.descripcion) LIKE CONCAT('%', LOWER(:filtroTexto), '%') OR " +
             "      str(p.id) LIKE CONCAT('%', :filtroTexto, '%') " +
             ") " +
             "AND rmi.estadoVerificacion = :estado " +
             "ORDER BY rmi.id ASC", countQuery = "SELECT COUNT(DISTINCT rmi) " +
                     "FROM RecepcionMercaderiaItem rmi " +
                     "JOIN rmi.notaRecepcionItem nri " +
                     "JOIN nri.producto p " +
                     "LEFT JOIN rmi.notaRecepcionItemDistribucion nrid " +
                     "LEFT JOIN nrid.sucursalEntrega s " +
                     "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
                     "AND ( :filtroTexto IS NULL OR :filtroTexto = '' OR " +
                     "      LOWER(p.descripcion) LIKE CONCAT('%', LOWER(:filtroTexto), '%') OR " +
                     "      str(p.id) LIKE CONCAT('%', :filtroTexto, '%') " +
                     ") " +
                     "AND rmi.estadoVerificacion = :estado ")
    Page<RecepcionMercaderiaItem> findByRecepcionMercaderiaIdPaginadosConEstado(
             @Param("recepcionId") Long recepcionId,
             @Param("filtroTexto") String filtroTexto,
             @Param("estado") EstadoVerificacion estado,
             Pageable pageable);

    /**
     * Busca un item pendiente de recepción por producto en una recepción específica
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
           "JOIN FETCH rmi.notaRecepcionItem nri " +
           "JOIN FETCH nri.producto p " +
           "JOIN FETCH rmi.notaRecepcionItemDistribucion nrid " +
           "JOIN FETCH nrid.sucursalEntrega s " +
           "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
           "AND p.id = :productoId " +
           "AND rmi.estadoVerificacion = 'PENDIENTE'")
    RecepcionMercaderiaItem findPendienteRecepcionItemPorProducto(
        @Param("recepcionId") Long recepcionId,
        @Param("productoId") Long productoId);

    /**
     * Obtiene el sumario de una recepción de mercadería
     */
    @Query("SELECT new com.franco.dev.graphql.operaciones.dto.RecepcionSumarioDTO(" +
           "rmi.recepcionMercaderia.id, " +
           "rmi.recepcionMercaderia.estado, " +
           "rmi.recepcionMercaderia.proveedor.persona.nombre, " +
           "rmi.recepcionMercaderia.sucursalRecepcion.nombre, " +
           "COUNT(rmi.id), " +
           "COUNT(CASE WHEN rmi.estadoVerificacion = 'VERIFICADO' THEN 1 END), " +
           "COUNT(CASE WHEN rmi.estadoVerificacion = 'PENDIENTE' THEN 1 END), " +
           "COUNT(CASE WHEN rmi.estadoVerificacion = 'VERIFICADO_CON_DIFERENCIA' THEN 1 END), " +
           "COUNT(CASE WHEN rmi.estadoVerificacion = 'RECHAZADO' THEN 1 END), " +
           "COUNT(DISTINCT rmi.notaRecepcionItem.producto.id), " +
           "COUNT(DISTINCT CASE WHEN rmi.estadoVerificacion = 'VERIFICADO' THEN rmi.notaRecepcionItem.producto.id END), " +
           "COUNT(DISTINCT CASE WHEN rmi.estadoVerificacion = 'PENDIENTE' THEN rmi.notaRecepcionItem.producto.id END), " +
           "rmi.recepcionMercaderia.fecha, " +
           "rmi.recepcionMercaderia.fecha) " +
           "FROM RecepcionMercaderiaItem rmi " +
           "WHERE rmi.recepcionMercaderia.id = :recepcionId " +
           "GROUP BY rmi.recepcionMercaderia.id, rmi.recepcionMercaderia.estado, " +
           "rmi.recepcionMercaderia.proveedor.persona.nombre, rmi.recepcionMercaderia.sucursalRecepcion.nombre, " +
           "rmi.recepcionMercaderia.fecha")
    RecepcionSumarioDTO findSumarioRecepcion(@Param("recepcionId") Long recepcionId);

    /**
     * Busca RecepcionMercaderiaItem por notaRecepcionItemId y recepcionMercaderiaId
     */
    @Query("SELECT rmi FROM RecepcionMercaderiaItem rmi " +
            "WHERE rmi.notaRecepcionItem.id = :notaRecepcionItemId " +
            "AND rmi.recepcionMercaderia.id = :recepcionMercaderiaId")
    List<RecepcionMercaderiaItem> findByNotaRecepcionItemIdAndRecepcionMercaderiaId(
            @Param("notaRecepcionItemId") Long notaRecepcionItemId,
            @Param("recepcionMercaderiaId") Long recepcionMercaderiaId);
}