package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VentaItemRepository extends HelperRepository<VentaItem, EmbebedPrimaryKey> {

        default Class<VentaItem> getEntityClass() {
                return VentaItem.class;
        }

        // public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String
        // texto);

        // @Query("select p from Venta p left outer join p.proveedor as pro left outer
        // join pro.persona as per where LOWER(per.nombre) like %?1%")
        // public List<Venta> findByProveedor(String texto);

        // @Query("select p from Producto p where CAST(id as text) like %?1% or
        // UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
        // public List<Producto> findbyAll(String texto);

        public List<VentaItem> findByVentaIdAndSucursalId(Long id, Long sucId);

        VentaItem findByIdAndSucursalId(Long id, Long sucId);

        Boolean deleteByIdAndSucursalId(Long id, Long sucId);

        @Query(value = "select sum(vi.cantidad * vi.precio) from operaciones.venta_item vi " +
                        "where vi.venta_id = ?1 and vi.sucursal_id = ?2", nativeQuery = true)
        Double totalByVentaIdAndSucId(Long id, Long sucId);

        @Query("SELECT  new com.franco.dev.domain.operaciones.VentaPorFuncionario(p.id, p.descripcion, SUM(vi.precio), SUM(CAST(vi.cantidad AS long))) "
                        +
                        "FROM VentaItem vi " +
                        "JOIN vi.venta v " +
                        "JOIN vi.producto p " +
                        "WHERE v.usuario.id = :usuarioId " +
                        "AND v.creadoEn BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY p.id, p.descripcion " +
                        "ORDER BY SUM(vi.cantidad) DESC")
        List<java.lang.Object> findTopProductoByUsuario(
                        @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
                        @org.springframework.data.repository.query.Param("inicio") java.time.LocalDateTime inicio,
                        @org.springframework.data.repository.query.Param("fin") java.time.LocalDateTime fin,
                        org.springframework.data.domain.Pageable pageable);
}