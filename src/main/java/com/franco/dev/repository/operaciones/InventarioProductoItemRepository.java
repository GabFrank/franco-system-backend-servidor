package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventarioProductoItemRepository extends HelperRepository<InventarioProductoItem, Long> {

    default Class<InventarioProductoItem> getEntityClass() {
        return InventarioProductoItem.class;
    }

    public List<InventarioProductoItem> findByInventarioProductoIdOrderByIdDesc(Long id, Pageable pageable);

    public List<InventarioProductoItem> findByInventarioProductoId(Long id);

    public List<InventarioProductoItem> findByInventarioProductoInventarioIdAndPresentacionProductoId(Long ipiProId, Long proId);

    @Query(value = "Select i from InventarioProductoItem i " +
            "join i.presentacion pre " +
            "join pre.producto pro " +
            "join i.inventarioProducto ip " +
            "join ip.inventario inv " +
            "join inv.sucursal s " +
            "join i.usuario u " +
            "where " +
            "i.creadoEn BETWEEN :startDate AND :endDate AND " +
            "((:sucursalIdList) is null or s.id IN (:sucursalIdList)) AND " +
            "((:usuarioIdList) is null or u.id IN (:usuarioIdList)) AND " +
            "((:productoIdList) is null or pro.id IN (:productoIdList)) " +
            "")
    public Page<InventarioProductoItem> findAllWithFilters(
            @Param("sucursalIdList") List<Long> sucursalIdList,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("usuarioIdList") List<Long> usuarioIdList,
            @Param("productoIdList") List<Long> productoIdList,
            Pageable pageable);

    @Query(value = "select ipi.* from operaciones.inventario_producto_item ipi " +
            "left join operaciones.inventario_producto ip on ip.id = ipi.inventario_producto_id " +
            "left join operaciones.inventario i on i.id = ip.inventario_id " +
            "left join productos.presentacion p on p.id = ipi.presentacion_id " +
            "left join productos.producto p2 on p2.id = p.producto_id " +
            "where i.id = ?1 and p2.id = ?2", nativeQuery = true)
    public List<InventarioProductoItem> findByInventarioIdAndProductoId(Long inventarioId, Long productoId);

}