package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto;
import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoStockRepository extends HelperRepository<MovimientoStock, Long> {
    default Class<MovimientoStock> getEntityClass() {
        return MovimientoStock.class;
    }

    public List<MovimientoStock> findByProductoId(String texto);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2")
    public Float stockByProductoIdAndSucursalId(Long proId, Long sucId);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2 and p.creadoEn < ?3")
    public Float stockByProductoIdAndSucursalIdAntesDeFecha(Long proId, Long sucId, LocalDateTime fecha);

    @Query("select new com.franco.dev.domain.operaciones.dto.MovimientoStockCantidadAndIdDto(COALESCE(SUM(p.cantidad), 0), MAX(p.id), count(p.id)) " +
            "from MovimientoStock p " +
            "left join p.producto pro " +
            "where p.estado = true and pro.id = ?1 and p.sucursalId = ?2 and p.id > ?3")
    public MovimientoStockCantidadAndIdDto stockByProductoIdAndSucursalIdAndLastId(Long proId, Long sucId, Long lastId);


    @Query(value = "select sum(ms.cantidad) from MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "(cast(:inicio as timestamp) IS NULL OR ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp))")
    public Double findStockWithFilters(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalList") List<Long> sucursalList,
            @Param("productoId") Long productoId,
            @Param("tipoMovimientoList") List<String> tipoMovimientoList,
            @Param("usuarioId") Long usuarioId);

    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1")
    public Float stockByProductoId(Long proId);


    @Query("select SUM(p.cantidad) from MovimientoStock p " +
            "left outer join p.producto as pro " +
            "where p.estado = true and pro.id = ?1 and p.id != ?2 and p.sucursalId = ?3")
    public Float stockByProductoIdExeptMovimientoId(Long proId, Long movId, Long sucId);

    @Query(value = "select * from operaciones.movimiento_stock p " +
            "left join productos.producto as pro on p.producto_id = pro.id " +
            "where pro.id = ?1 " +
            "and cast(p.tipo_movimiento as text) = ?2 " +
            "order by p.creado_en desc limit ?3",
            nativeQuery = true)
    public List<MovimientoStock> ultimosMovimientosPorProductoId(Long id, String tm, Integer size);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.movimiento_stock ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)", nativeQuery = true)
    public List<MovimientoStock> findByDate(String inicio, String fin);

    public List<MovimientoStock> findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento tipoMovimiento, Long referencia, Long sucId);

    public MovimientoStock findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento tipoMovimiento, Long referencia, Long sucId, Long prodId);

    public List<MovimientoStock> findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento tipoMovimiento, Long referencia);

    @Query(value = "select ms from MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp) " +
            "order by ms.creadoEn")
    public Page<MovimientoStock> findByFilters(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("sucursalList") List<Long> sucursalList,
            @Param("productoId") Long productoId,
            @Param("tipoMovimientoList") List<String> tipoMovimientoList,
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    @Query(value = "SELECT new com.franco.dev.domain.dto.StockPorTipoMovimientoDto(ms.tipoMovimiento, SUM(ms.cantidad)) " +
            "FROM MovimientoStock ms " +
            "join ms.producto p " +
            "left join ms.usuario u " +
            "where " +
            "((:usuarioId) is null or u.id = (:usuarioId) ) and " +
            "((:sucursalList) is null or ms.sucursalId in (:sucursalList) ) and " +
            "((:productoId) is null or p.id = (:productoId)) and " +
            "((:tipoMovimientoList) IS NULL OR cast(ms.tipoMovimiento as text) IN :tipoMovimientoList) and " +
            "ms.creadoEn between cast((:inicio) as timestamp) and cast((:fin) as timestamp) and ms.estado = true " +
            "group by ms.tipoMovimiento")
    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(@Param("inicio") LocalDateTime inicio,
                                                                      @Param("fin") LocalDateTime fin,
                                                                      @Param("sucursalList") List<Long> sucursalList,
                                                                      @Param("productoId") Long productoId,
                                                                      @Param("tipoMovimientoList") List<String> tipoMovimientoList,
                                                                      @Param("usuarioId") Long usuarioId);

    @Query("SELECT MAX(m.id) FROM MovimientoStock m WHERE m.sucursalId = :sucursalId AND m.id % 2 = 1")
    Long findMaxOddIdByProductoIdAndSucursalId(@Param("sucursalId") Long sucursalId);

    @Query("SELECT MAX(e.id) FROM MovimientoStock e WHERE e.sucursalId = :sucursalId")
    Long findMaxId(@Param("sucursalId") Long sucursalId);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.ProductoSaldoDto(ms.producto.id, ms.producto.descripcion, ms.sucursalId, SUM(ms.cantidad)) " +
            "FROM MovimientoStock ms " +
            "JOIN ms.producto p " +
            "WHERE ms.estado = true " +
            "AND (:sucursalId IS NULL OR ms.sucursalId = :sucursalId) " +
            "GROUP BY ms.producto.id, ms.producto.descripcion, ms.sucursalId " +
            "HAVING SUM(ms.cantidad) > 0 " +
            "ORDER BY SUM(ms.cantidad) DESC")
    Page<ProductoSaldoDto> findProductosConCantidadPositiva(@Param("sucursalId") Long sucursalId, Pageable pageable);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.ProductoSaldoDto(ms.producto.id, ms.producto.descripcion, ms.sucursalId, SUM(ms.cantidad)) " +
            "FROM MovimientoStock ms " +
            "JOIN ms.producto p " +
            "WHERE ms.estado = true " +
            "AND (:sucursalId IS NULL OR ms.sucursalId = :sucursalId) " +
            "GROUP BY ms.producto.id, ms.producto.descripcion, ms.sucursalId " +
            "HAVING SUM(ms.cantidad) < 0 " +
            "ORDER BY SUM(ms.cantidad) ASC")
    Page<ProductoSaldoDto> findProductosConCantidadNegativa(@Param("sucursalId") Long sucursalId, Pageable pageable);

    @Query("SELECT new com.franco.dev.domain.operaciones.dto.ProductoSaldoDto(p.id, p.descripcion, CAST(:sucursalId AS long), COALESCE(SUM(ms.cantidad), 0.0)) " +
            "FROM Producto p " +
            "LEFT JOIN MovimientoStock ms ON ms.producto.id = p.id AND ms.sucursalId = :sucursalId AND ms.estado = true " +
            "WHERE p.activo = true " +
            "AND p.id NOT IN (" +
            "    SELECT DISTINCT ipi.presentacion.producto.id " +
            "    FROM InventarioProductoItem ipi " +
            "    JOIN ipi.inventarioProducto ip " +
            "    JOIN ip.inventario inv " +
            "    WHERE inv.sucursal.id = :sucursalId " +
            "    AND ipi.creadoEn BETWEEN :fechaInicio AND :fechaFin" +
            ") " +
            "GROUP BY p.id, p.descripcion " +
            "ORDER BY p.descripcion ASC")
    Page<ProductoSaldoDto> findProductosFaltantes(
            @Param("sucursalId") Long sucursalId, 
            @Param("fechaInicio") LocalDateTime fechaInicio, 
            @Param("fechaFin") LocalDateTime fechaFin, 
            Pageable pageable);

}

//    private LocalDateTime fechaInicio;
//    private LocalDateTime fechaFin;
//    private Double stockPorRangoFecha;
//    private List<StockPorTipoMovimientoDto> stockPorTipoMovimientoList;
//    private Producto producto;
//    private List<Long> sucursalId;
//    private List<TipoMovimiento> tipoMovimientoList;
//    private Usuario usuario;