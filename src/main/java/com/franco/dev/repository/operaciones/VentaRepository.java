package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends HelperRepository<Venta, EmbebedPrimaryKey> {
    default Class<Venta> getEntityClass() {
        return Venta.class;
    }

//    public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    public List<Venta> findAllByCajaIdAndSucursalId(Long id, Long sucId, Pageable pageable);

    public List<Venta> findByCajaIdAndCajaSucursalId(Long id, Long sucId);

    @Query(value = "select " +
            "* " +
            "from " +
            "operaciones.venta v " +
            "where " +
            "v.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)" +
            "order by " +
            "v.id", nativeQuery = true)
    public List<Venta> ventaPorPeriodo(LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "select v from Venta v, CobroDetalle cd " +
            "join v.caja ca " +
            "join v.cobro c " +
            "join cd.cobro c2 " +
            "join cd.formaPago fp " +
            "where ca.id = :id and v.sucursalId = :sucId and c = c2 and " +
            "(:formaPagoId is null or fp.id = :formaPagoId) and " +
            "(v.estado = :estado or cast(:estado as com.franco.dev.domain.operaciones.enums.VentaEstado) is null) group by (v.id, v.sucursalId)")
    public List<Venta> findWithFilters(Long id, Long sucId, Long formaPagoId, VentaEstado estado, Pageable pageable);

}