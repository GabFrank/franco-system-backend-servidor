package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends HelperRepository<Venta, EmbebedPrimaryKey>, JpaSpecificationExecutor<Venta> {
        default Class<Venta> getEntityClass() {
                return Venta.class;
        }

        public Venta findByIdAndSucursalId(Long id, Long sucId);

        public Venta findByDeliveryIdAndSucursalId(Long deliveryId, Long sucursalId);

        // public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String
        // texto);

        // @Query("select p from Venta p left outer join p.proveedor as pro left outer
        // join pro.persona as per where LOWER(per.nombre) like %?1%")
        // public List<Venta> findByProveedor(String texto);

        // @Query("select p from Producto p where CAST(id as text) like %?1% or
        // LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
        // public List<Producto> findbyAll(String texto);

        public Page<Venta> findAllByCajaIdAndSucursalIdOrderByIdAsc(Long id, Long sucId, Pageable pageable);

        public Page<Venta> findAllByCajaIdAndSucursalIdOrderByIdDesc(Long id, Long sucId, Pageable pageable);

        // public List<Venta> findByCajaIdAndCajaSucursalId(Long id, Long sucId);
        // @Query(value = "select * from operaciones.venta v where v.caja_id = ?1 and
        // v.sucursal_id = ?2", nativeQuery = true)
        @Query("SELECT v FROM Venta v " +
                        "JOIN FETCH v.cobro " +
                        "join v.caja ca " +
                        "WHERE ca.id = :id AND v.sucursalId = :sucId")
        List<Venta> findByCajaIdAndCajaSucursalId(Long id, Long sucId);

        public List<Venta> findAllByCajaIdAndSucursalIdAndEstadoIn(Long id, Long sucId,
                        List<DeliveryEstado> estadoList);

        @Query(value = "select " +
                        "* " +
                        "from " +
                        "operaciones.venta v " +
                        "where " +
                        "v.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)" +
                        "order by " +
                        "v.id", nativeQuery = true)
        public List<Venta> ventaPorPeriodo(LocalDateTime inicio, LocalDateTime fin);

        public List<Venta> findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(Long sucId, LocalDateTime inicio,
                        LocalDateTime fin);

        public List<Venta> findByUsuarioIdAndCreadoEnBetweenOrderByIdDesc(Long usuarioId, LocalDateTime inicio,
                        LocalDateTime fin);

        @Query(value = "SELECT u.id, p.nombre, SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "JOIN personas.usuario u ON v.usuario_id = u.id " +
                        "JOIN personas.persona p ON u.persona_id = p.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.sucursal_id = :sucId " +
                        "AND u.id = :usuarioId " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY u.id, p.nombre " +
                        "ORDER BY SUM(v.total_gs) DESC", nativeQuery = true)
        List<Object[]> getVentasPorFuncionario(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId,
                        @Param("usuarioId") Long usuarioId,
                        org.springframework.data.domain.Pageable pageable);

        @Query(value = "SELECT u.id, p.nombre, SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "JOIN personas.usuario u ON v.usuario_id = u.id " +
                        "JOIN personas.persona p ON u.persona_id = p.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.sucursal_id = :sucId " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY u.id, p.nombre " +
                        "ORDER BY SUM(v.total_gs) DESC", nativeQuery = true)
        List<Object[]> getVentasPorFuncionarioBySucursal(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId,
                        org.springframework.data.domain.Pageable pageable);

        @Query(value = "SELECT u.id, p.nombre, SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "JOIN personas.usuario u ON v.usuario_id = u.id " +
                        "JOIN personas.persona p ON u.persona_id = p.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND u.id = :usuarioId " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY u.id, p.nombre " +
                        "ORDER BY SUM(v.total_gs) DESC", nativeQuery = true)
        List<Object[]> getVentasPorFuncionarioByUsuario(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("usuarioId") Long usuarioId,
                        org.springframework.data.domain.Pageable pageable);

        @Query(value = "SELECT u.id, p.nombre, SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "JOIN personas.usuario u ON v.usuario_id = u.id " +
                        "JOIN personas.persona p ON u.persona_id = p.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY u.id, p.nombre " +
                        "ORDER BY SUM(v.total_gs) DESC", nativeQuery = true)
        List<Object[]> getVentasPorFuncionarioAll(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin,
                        org.springframework.data.domain.Pageable pageable);
    @Query(value = "SELECT \n" +
            "SUM(CASE WHEN m.denominacion = 'GUARANI' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaGs, \n" +
            "SUM(CASE WHEN m.denominacion = 'REAL' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaRs, \n" +
            "SUM(CASE WHEN m.denominacion = 'DOLAR' AND fp.descripcion = 'EFECTIVO' AND cd.pago THEN cd.valor ELSE 0 END) AS totalVentaDs, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TARJETA' AND m.denominacion = 'GUARANI' THEN cd.valor ELSE 0 END) AS totalTarjeta, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TARJETA' AND m.denominacion = 'REAL' THEN cd.valor ELSE 0 END) AS totalTarjetaRs, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TARJETA' AND m.denominacion = 'DOLAR' THEN cd.valor ELSE 0 END) AS totalTarjetaDs, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TRANSFERENCIA' AND m.denominacion = 'GUARANI' THEN cd.valor ELSE 0 END) AS totalTransferencia, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TRANSFERENCIA' AND m.denominacion = 'REAL' THEN cd.valor ELSE 0 END) AS totalTransferenciaRs, \n" +
            "SUM(CASE WHEN fp.descripcion = 'TRANSFERENCIA' AND m.denominacion = 'DOLAR' THEN cd.valor ELSE 0 END) AS totalTransferenciaDs, \n" +
            "SUM(CASE WHEN fp.descripcion = 'CONVENIO' THEN cd.valor ELSE 0 END) AS totalConvenio, \n" +
            "SUM(CASE WHEN cd.descuento THEN cd.valor ELSE 0 END) AS totalDescuento, \n" +
            "SUM(CASE WHEN cd.aumento THEN cd.valor ELSE 0 END) AS totalAumento, \n" +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'GUARANI' THEN cd.valor ELSE 0 END) AS vueltoGs, \n" +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'REAL' THEN cd.valor ELSE 0 END) AS vueltoRs, \n" +
            "SUM(CASE WHEN cd.vuelto AND m.denominacion = 'DOLAR' THEN cd.valor ELSE 0 END) AS vueltoDs, \n" +
            "(SELECT SUM(v2.total_gs + coalesce(pd.valor, 0)) FROM operaciones.venta v2 \n" +
            "\tleft join operaciones.delivery d on v2.delivery_id = d.id and v2.sucursal_id = d.sucursal_id \n" +
            "\tleft join operaciones.precio_delivery pd on d.precio_delivery_id = pd.id\n" +
            "\tWHERE v2.caja_id = :cajaId AND v2.sucursal_id = :sucursalId AND v2.estado IN ('CONCLUIDA', 'EN_VERIFICACION')) AS totalGeneral \n" +
            "FROM operaciones.venta v \n" +
            "JOIN operaciones.cobro c ON v.cobro_id = c.id AND v.sucursal_id = c.sucursal_id \n" +
            "JOIN operaciones.cobro_detalle cd ON cd.cobro_id = c.id AND cd.sucursal_id = c.sucursal_id \n" +
            "JOIN financiero.moneda m ON cd.moneda_id = m.id \n" +
            "JOIN financiero.forma_pago fp ON cd.forma_pago_id = fp.id \n" +
            "WHERE v.caja_id = :cajaId \n" +
            "AND v.sucursal_id = :sucursalId \n" +
            "AND v.estado IN ('CONCLUIDA', 'EN_VERIFICACION')", nativeQuery = true)
    List<Object[]> sumarioVentasPorCajaAndSurusal(@Param("cajaId") Long cajaId, @Param("sucursalId") Long sucursalId);
        @Query("SELECT DISTINCT CAST(v.sucursalId AS string) FROM Venta v WHERE v.usuario.id = :usuarioId AND v.creadoEn BETWEEN :inicio AND :fin AND v.estado = 'CONCLUIDA'")
        List<String> findSucursalesByUsuario(@Param("usuarioId") Long usuarioId, @Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        @Query(value = "select v from Venta v " +
                        "join v.caja ca " +
                        "join v.cobro c " +
                        "JOIN CobroDetalle cd ON cd.cobro = c " +
                        "join cd.formaPago fp " +
                        "join cd.moneda m " +
                        "where ca.id = :id and v.sucursalId = :sucId and " +
                        "(:formaPagoId is null or fp.id = :formaPagoId) and " +
                        "(:monedaId is null or m.id = :monedaId) and " +
                        "(:conDescuento = false or cd.descuento = true) and " +
                        "(:conAumento = false or cd.aumento = true) and " +
                        "(v.estado = :estado or cast(:estado as com.franco.dev.domain.operaciones.enums.VentaEstado) is null) group by (v.id, v.sucursalId)")
        public Page<Venta> findWithFilters(Long id, Long sucId, Long formaPagoId, VentaEstado estado, Pageable pageable,
                        Long monedaId, @Param("conDescuento") Boolean conDescuento,
                        @Param("conAumento") Boolean conAumento);

        // @Query(value = "select v from Venta v, CobroDetalle cd, Delivery d " +
        // "join v.caja ca " +
        // "join v.cobro c " +
        // "join cd.cobro c2 " +
        // "join cd.formaPago fp " +
        // "join d.venta v2 " +
        // "join cd.moneda m " +
        // "where ca.id = :id and v.sucursalId = :sucId and c = c2 and " +
        // "(:isDelivery = true and v2.id = v.id) and " +
        // "(:formaPagoId is null or fp.id = :formaPagoId) and " +
        // "(:monedaId is null or m.id = :monedaId) and " +
        // "(v.estado = :estado or cast(:estado as
        // com.franco.dev.domain.operaciones.enums.VentaEstado) is null) group by (v.id,
        // v.sucursalId)")
        // public Page<Venta> findWithFilters(Long id, Long sucId, Long formaPagoId,
        // VentaEstado estado, Pageable pageable, Boolean isDelivery, Long monedaId);

        @Query(value = "SELECT s.id, s.nombre, " +
                        "SUM((CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END)) " +
                        "FROM operaciones.venta v " +
                        "JOIN empresarial.sucursal s ON v.sucursal_id = s.id " +
                        "JOIN operaciones.cobro_detalle cd ON cd.cobro_id = v.cobro_id AND cd.sucursal_id = v.sucursal_id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "AND cd.valor < 2000000000 " +
                        "GROUP BY s.id, s.nombre " +
                        "ORDER BY SUM((CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END)) DESC", nativeQuery = true)
        List<Object[]> getVentasPorSucursal(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        public List<Venta> findAllByCajaIdAndSucursalIdAndDeliveryEstadoIn(Long id, Long sucId,
                        List<DeliveryEstado> estadoList);

        @Query(value = "SELECT CAST(extract(hour from v.creado_en) as integer), SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.sucursal_id = :sucId " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY extract(hour from v.creado_en) " +
                        "ORDER BY extract(hour from v.creado_en)", nativeQuery = true)
        List<Object[]> ventasPorHora(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

        @Query(value = "SELECT CAST(extract(hour from v.creado_en) as integer), SUM(v.total_gs), COUNT(v.id) " +
                        "FROM operaciones.venta v " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "GROUP BY extract(hour from v.creado_en) " +
                        "ORDER BY extract(hour from v.creado_en)", nativeQuery = true)
        List<Object[]> ventasPorHoraSinSucursal(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

        @Query(value = "SELECT CAST(extract(month from v.creado_en) as integer) as mes, " +
                        "SUM((CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END)) as total, " +
                        "COUNT(DISTINCT v.id) as cantidad, " +
                        "SUM(CASE WHEN fp.descripcion = 'EFECTIVO' THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as efvo, " +
                        "SUM(CASE WHEN fp.descripcion = 'TARJETA' THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as tarjeta, " +
                        "SUM(CASE WHEN fp.descripcion NOT IN ('EFECTIVO', 'TARJETA') THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as otros " +
                        "FROM operaciones.venta v " +
                        "JOIN operaciones.cobro_detalle cd ON cd.cobro_id = v.cobro_id AND cd.sucursal_id = v.sucursal_id " +
                        "JOIN financiero.forma_pago fp ON cd.forma_pago_id = fp.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.sucursal_id = :sucId " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "AND cd.pago = true " +
                        "AND cd.valor < 2000000000 " +
                        "GROUP BY extract(month from v.creado_en) " +
                        "ORDER BY extract(month from v.creado_en)", nativeQuery = true)
        List<Object[]> ventasPorMes(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin, @Param("sucId") Long sucId);

        @Query(value = "SELECT CAST(extract(month from v.creado_en) as integer) as mes, " +
                        "SUM((CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END)) as total, " +
                        "COUNT(DISTINCT v.id) as cantidad, " +
                        "SUM(CASE WHEN fp.descripcion = 'EFECTIVO' THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as efvo, " +
                        "SUM(CASE WHEN fp.descripcion = 'TARJETA' THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as tarjeta, " +
                        "SUM(CASE WHEN fp.descripcion NOT IN ('EFECTIVO', 'TARJETA') THEN (CASE WHEN cd.pago = true THEN cd.valor * cd.cambio ELSE 0 END) - (CASE WHEN cd.vuelto = true THEN cd.valor * cd.cambio ELSE 0 END) ELSE 0 END) as otros " +
                        "FROM operaciones.venta v " +
                        "JOIN operaciones.cobro_detalle cd ON cd.cobro_id = v.cobro_id AND cd.sucursal_id = v.sucursal_id " +
                        "JOIN financiero.forma_pago fp ON cd.forma_pago_id = fp.id " +
                        "WHERE v.creado_en BETWEEN :inicio AND :fin " +
                        "AND v.estado = 'CONCLUIDA' " +
                        "AND cd.pago = true " +
                        "AND cd.valor < 2000000000 " +
                        "GROUP BY extract(month from v.creado_en) " +
                        "ORDER BY extract(month from v.creado_en)", nativeQuery = true)
        List<Object[]> ventasPorMesSinSucursal(@Param("inicio") LocalDateTime inicio,
                        @Param("fin") LocalDateTime fin);

}