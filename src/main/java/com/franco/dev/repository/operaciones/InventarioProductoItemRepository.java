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

}