package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.input.PdvCajaSumarioDto;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends HelperRepository<Venta, EmbebedPrimaryKey>, JpaSpecificationExecutor {
    default Class<Venta> getEntityClass() {
        return Venta.class;
    }

    public Venta findByIdAndSucursalId(Long id, Long sucId);

    public Venta findByDeliveryIdAndSucursalId(Long deliveryId, Long sucursalId);

//    public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    public Page<Venta> findAllByCajaIdAndSucursalIdOrderByIdAsc(Long id, Long sucId, Pageable pageable);

    public Page<Venta> findAllByCajaIdAndSucursalIdOrderByIdDesc(Long id, Long sucId, Pageable pageable);

    //        public List<Venta> findByCajaIdAndCajaSucursalId(Long id, Long sucId);
//    @Query(value = "select * from operaciones.venta v where v.caja_id = ?1 and v.sucursal_id = ?2", nativeQuery = true)
    @Query("SELECT v FROM Venta v " +
            "JOIN FETCH v.cobro " +
            "join v.caja ca " +
            "WHERE ca.id = :id AND v.sucursalId = :sucId")
    List<Venta> findByCajaIdAndCajaSucursalId(Long id, Long sucId);

    public List<Venta> findAllByCajaIdAndSucursalIdAndEstadoIn(Long id, Long sucId,  List<DeliveryEstado> estadoList);

    @Query(value = "select " +
            "* " +
            "from " +
            "operaciones.venta v " +
            "where " +
            "v.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)" +
            "order by " +
            "v.id", nativeQuery = true)
    public List<Venta> ventaPorPeriodo(LocalDateTime inicio, LocalDateTime fin);

    public List<Venta> findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(Long sucId, LocalDateTime inicio, LocalDateTime fin);

    @Query(value = "select v from Venta v " +
            "join v.caja ca " +
            "join v.cobro c " +
            "JOIN CobroDetalle cd ON cd.cobro = c " +
            "join cd.formaPago fp " +
            "join cd.moneda m " +
            "where ca.id = :id and v.sucursalId = :sucId and c = c2 and " +
            "(:formaPagoId is null or fp.id = :formaPagoId) and " +
            "(:monedaId is null or m.id = :monedaId) and " +
            "(v.estado = :estado or cast(:estado as com.franco.dev.domain.operaciones.enums.VentaEstado) is null) group by (v.id, v.sucursalId)")
    public Page<Venta> findWithFilters(Long id, Long sucId, Long formaPagoId, VentaEstado estado, Pageable pageable, Long monedaId);

//    @Query(value = "select v from Venta v, CobroDetalle cd, Delivery d " +
//            "join v.caja ca " +
//            "join v.cobro c " +
//            "join cd.cobro c2 " +
//            "join cd.formaPago fp " +
//            "join d.venta v2 " +
//            "join cd.moneda m " +
//            "where ca.id = :id and v.sucursalId = :sucId and c = c2 and " +
//            "(:isDelivery = true and v2.id = v.id) and " +
//            "(:formaPagoId is null or fp.id = :formaPagoId) and " +
//            "(:monedaId is null or m.id = :monedaId) and " +
//            "(v.estado = :estado or cast(:estado as com.franco.dev.domain.operaciones.enums.VentaEstado) is null) group by (v.id, v.sucursalId)")
//    public Page<Venta> findWithFilters(Long id, Long sucId, Long formaPagoId, VentaEstado estado, Pageable pageable, Boolean isDelivery, Long monedaId);


//    @Query(value = "select s.id as sucId, s.nombre as nombre, sum(v.totalGs) as total from Venta v " +
//            "join v.sucursalId s " +
//            "where (fl.creadoEn between :inicio and :fin) " +
//            "group by nombre order by total desc")
//    public List<VentaPorSucursal> ventasPorSucursal(LocalDateTime inicio, LocalDateTime fin);

    public List<Venta> findAllByCajaIdAndSucursalIdAndDeliveryEstadoIn(Long id, Long sucId,  List<DeliveryEstado> estadoList);

    @Query(value = "SELECT " +
            "SUM(CASE WHEN m.denominacion = 'GUARANI' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaGs, " +
            "SUM(CASE WHEN m.denominacion = 'REAL' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaRs, " +
            "SUM(CASE WHEN m.denominacion = 'DOLAR' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaDs, " +
            "SUM(CASE WHEN fp.descripcion = 'TARJETA' THEN cd.valor ELSE 0 END) AS totalTarjeta, " +
            "SUM(CASE WHEN fp.descripcion = 'CONVENIO' THEN cd.valor ELSE 0 END) AS totalConvenio, " +
            "SUM(CASE WHEN cd.descuento THEN cd.valor ELSE 0 END) AS totalDescuento, " +
            "SUM(CASE WHEN cd.aumento THEN cd.valor ELSE 0 END) AS totalAumento, " +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'GUARANI' THEN cd.valor ELSE 0 END) AS vueltoGs, " +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'REAL' THEN cd.valor ELSE 0 END) AS vueltoRs, " +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'DOLAR' THEN cd.valor ELSE 0 END) AS vueltoDs, " +
            "(SELECT SUM(v2.total_gs) FROM operaciones.venta v2 WHERE v2.caja_id = :cajaId AND v2.sucursal_id = :sucursalId AND v2.estado IN ('CONCLUIDA', 'EN_VERIFICACION')) AS totalGeneral " +
            "FROM operaciones.venta v " +
            "JOIN operaciones.cobro c ON v.cobro_id = c.id AND v.sucursal_id = c.sucursal_id " +
            "JOIN operaciones.cobro_detalle cd ON cd.cobro_id = c.id AND cd.sucursal_id = c.sucursal_id " +
            "JOIN financiero.moneda m ON cd.moneda_id = m.id " +
            "JOIN financiero.forma_pago fp ON cd.forma_pago_id = fp.id " +
            "WHERE v.caja_id = :cajaId " +
            "AND v.sucursal_id = :sucursalId " +
            "AND v.estado IN ('CONCLUIDA', 'EN_VERIFICACION')", nativeQuery = true)
    List<Object[]> sumarioVentasPorCajaAndSurusal(@Param("cajaId") Long cajaId, @Param("sucursalId") Long sucursalId);


}