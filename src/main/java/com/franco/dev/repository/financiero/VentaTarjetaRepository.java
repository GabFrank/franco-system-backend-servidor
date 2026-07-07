package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface VentaTarjetaRepository extends HelperRepository<VentaTarjeta, EmbebedPrimaryKey> {

    default Class<VentaTarjeta> getEntityClass() {
        return VentaTarjeta.class;
    }

    VentaTarjeta findFirstByVentaIdAndSucursalIdAndEstadoOrderByIdAsc(Long ventaId, Long sucursalId, String estado);

    VentaTarjeta findFirstByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    VentaTarjeta findByIdAndSucursalId(Long id, Long sucursalId);

    List<VentaTarjeta> findByCajaIdAndSucursalIdOrderByCreadoEnDesc(Long cajaId, Long sucursalId);

    List<VentaTarjeta> findByVentaIdAndSucursalIdOrderByIdAsc(Long ventaId, Long sucursalId);

    Long countByCajaIdAndSucursalIdAndEstado(Long cajaId, Long sucursalId, String estado);

    @Query(value = "SELECT vt FROM VentaTarjeta vt " +
            "WHERE (:id IS NULL OR vt.id = :id) " +
            "AND (:ventaId IS NULL OR vt.venta.id = :ventaId) " +
            "AND (:sucursalId IS NULL OR vt.sucursalId = :sucursalId) " +
            "AND (:terminalDescripcion IS NULL OR vt.terminalPos.descripcion = :terminalDescripcion) " +
            "AND (:terminalCodigo IS NULL OR vt.terminalPos.codigo = :terminalCodigo) " +
            "AND (:estado IS NULL OR vt.estado = :estado) " +
            "AND (cast(:fechaDesde as timestamp) IS NULL OR vt.creadoEn >= :fechaDesde) " +
            "AND (cast(:fechaHasta as timestamp) IS NULL OR vt.creadoEn <= :fechaHasta) " +
            "ORDER BY vt.creadoEn DESC",
            countQuery = "SELECT COUNT(vt) FROM VentaTarjeta vt " +
            "WHERE (:id IS NULL OR vt.id = :id) " +
            "AND (:ventaId IS NULL OR vt.venta.id = :ventaId) " +
            "AND (:sucursalId IS NULL OR vt.sucursalId = :sucursalId) " +
            "AND (:terminalDescripcion IS NULL OR vt.terminalPos.descripcion = :terminalDescripcion) " +
            "AND (:terminalCodigo IS NULL OR vt.terminalPos.codigo = :terminalCodigo) " +
            "AND (:estado IS NULL OR vt.estado = :estado) " +
            "AND (cast(:fechaDesde as timestamp) IS NULL OR vt.creadoEn >= :fechaDesde) " +
            "AND (cast(:fechaHasta as timestamp) IS NULL OR vt.creadoEn <= :fechaHasta)")
    Page<VentaTarjeta> filterVentaTarjeta(
            @Param("id") Long id,
            @Param("ventaId") Long ventaId,
            @Param("sucursalId") Long sucursalId,
            @Param("terminalDescripcion") String terminalDescripcion,
            @Param("terminalCodigo") String terminalCodigo,
            @Param("estado") String estado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable);

    @Query("SELECT vt FROM VentaTarjeta vt " +
            "WHERE (:id IS NULL OR vt.id = :id) " +
            "AND (:ventaId IS NULL OR vt.venta.id = :ventaId) " +
            "AND (:sucursalId IS NULL OR vt.sucursalId = :sucursalId) " +
            "AND (:terminalDescripcion IS NULL OR vt.terminalPos.descripcion = :terminalDescripcion) " +
            "AND (:terminalCodigo IS NULL OR vt.terminalPos.codigo = :terminalCodigo) " +
            "AND (:estado IS NULL OR vt.estado = :estado) " +
            "AND (cast(:fechaDesde as timestamp) IS NULL OR vt.creadoEn >= :fechaDesde) " +
            "AND (cast(:fechaHasta as timestamp) IS NULL OR vt.creadoEn <= :fechaHasta) " +
            "ORDER BY vt.creadoEn DESC")
    List<VentaTarjeta> filterVentaTarjetaList(
            @Param("id") Long id,
            @Param("ventaId") Long ventaId,
            @Param("sucursalId") Long sucursalId,
            @Param("terminalDescripcion") String terminalDescripcion,
            @Param("terminalCodigo") String terminalCodigo,
            @Param("estado") String estado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta);
}
