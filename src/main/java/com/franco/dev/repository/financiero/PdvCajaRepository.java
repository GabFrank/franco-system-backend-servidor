package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.repository.HelperRepositoryEmbeddedId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, EmbebedPrimaryKey>, JpaSpecificationExecutor<PdvCaja> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

//    @Query(value = "select * from financiero.pdv_caja ms " +
//            "where ms.sucursal_id = ?1 order by ms.id desc", nativeQuery = true)
    public List<PdvCaja> findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(Long id, LocalDateTime inicio, LocalDateTime fin);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

//    @Query(value = "select * from financiero.pdv_caja pc \n" +
//            "where \n" +
//            "(:maletin_id is null or pc.maletin_id = :maletin_id) and \n" +
//            "(:cajero_id is null or pc.usuario_id = :cajero_id) and \n" +
//            "((:fecha_inicio is null and :fecha_fin is null) or pc.creado_en between cast(:fecha_inicio as timestamp) and cast(:fecha_fin as timestamp))\n")
//    public List<PdvCaja> findByAll();

    public List<PdvCaja> findByUsuarioIdAndActivo(Long id, Boolean activo);

    public PdvCaja findByUsuarioIdAndActivoAndSucursalId(Long id, Boolean activo, Long sucId);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

    List<PdvCaja> findByUsuarioIdOrderByIdDesc(Long id, Pageable pageable);

    Boolean deleteByIdAndSucursalId(Long id, Long sucId);

    PdvCaja findByIdAndSucursalId(Long id, Long sucId);

    @Query(value = "select c from PdvCaja c " +
            "join c.maletin m " +
            "join c.usuario u " +
            "where c.sucursalId = :sucId and " +
            "(:cajaId is null or c.id = :cajaId) and " +
            "(:maletinId is null or m.id = :maletinId) and " +
            "(:cajeroId is null or u.id = :cajeroId) and " +
            "(:verificado is null or c.verificado = :verificado) and " +
            "((:cajaId is not null) or (cast(:fechaInicio as timestamp) is null or cast(:fechaFin as timestamp) is null) or c.creadoEn between :fechaInicio and :fechaFin) and " +
            "(c.estado = :estado or cast(:estado as com.franco.dev.domain.financiero.enums.PdvCajaEstado) is null) order by c.creadoEn desc")
    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, Pageable pageable);

    List<PdvCaja> findBySucursalIdAndActivo(Long sucursalId, Boolean activo);

    @Query("SELECT DISTINCT v.caja FROM VentaObservacion vo JOIN vo.venta v WHERE v.caja IS NOT NULL")
    List<PdvCaja> findCajasWithVentaObservaciones();

    @Query("SELECT c FROM PdvCaja c WHERE c.maletin.id = :maletinId AND c.fechaCierre < :fechaApertura AND c.conteoCierre IS NOT NULL ORDER BY c.fechaCierre DESC")
    List<PdvCaja> findPreviousCajaByMaletinAndFecha(Long maletinId, LocalDateTime fechaApertura, Pageable pageable);

    @Query(value = "SELECT c.* FROM financiero.pdv_caja c " +
            "LEFT JOIN LATERAL (SELECT SUM(cm.cantidad * mb.valor) AS total_apertura FROM financiero.conteo_moneda cm JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id WHERE cm.conteo_id = c.conteo_apertura_id) ca ON TRUE " +
            "LEFT JOIN LATERAL (SELECT SUM(cm.cantidad * mb.valor) AS total_cierre FROM financiero.conteo_moneda cm JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id WHERE cm.conteo_id = c.conteo_cierre_id) cc ON TRUE " +
            "LEFT JOIN financiero.maletin m ON c.maletin_id = m.id " +
            "WHERE (:sucId IS NULL OR c.sucursal_id = CAST(:sucId AS bigint)) " +
            "AND (:cajaId IS NULL OR c.id = CAST(:cajaId AS bigint)) " +
            "AND (:maletinId IS NULL OR m.id = CAST(:maletinId AS bigint)) " +
            "AND (:cajeroId IS NULL OR c.usuario_id = CAST(:cajeroId AS bigint)) " +
            "AND (:verificado IS NULL OR c.verificado = :verificado) " +
            "AND ((:fechaInicio IS NULL OR :fechaFin IS NULL) OR c.creado_en BETWEEN :fechaInicio AND :fechaFin) " +
            "AND (:estado IS NULL OR c.estado = CAST(:estado AS TEXT)) " +
            "AND ( :difEstado IS NULL " +
            "      OR (:difEstado = 'CON_DIFERENCIA'  AND ca.total_apertura IS NOT NULL AND cc.total_cierre IS NOT NULL AND ca.total_apertura <> cc.total_cierre) " +
            "      OR (:difEstado = 'SIN_DIFERENCIA' AND ca.total_apertura IS NOT NULL AND cc.total_cierre IS NOT NULL AND ca.total_apertura = cc.total_cierre) " +
            "      OR (:difEstado = 'SIN_DATOS'      AND (ca.total_apertura IS NULL OR cc.total_cierre IS NULL)) ) " +
            "ORDER BY c.fecha_cierre DESC",
            countQuery = "SELECT COUNT(*) FROM financiero.pdv_caja c " +
            "LEFT JOIN LATERAL (SELECT SUM(cm.cantidad * mb.valor) AS total_apertura FROM financiero.conteo_moneda cm JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id WHERE cm.conteo_id = c.conteo_apertura_id) ca ON TRUE " +
            "LEFT JOIN LATERAL (SELECT SUM(cm.cantidad * mb.valor) AS total_cierre FROM financiero.conteo_moneda cm JOIN financiero.moneda_billetes mb ON mb.id = cm.moneda_billetes_id WHERE cm.conteo_id = c.conteo_cierre_id) cc ON TRUE " +
            "LEFT JOIN financiero.maletin m ON c.maletin_id = m.id " +
            "WHERE (:sucId IS NULL OR c.sucursal_id = CAST(:sucId AS bigint)) " +
            "AND (:cajaId IS NULL OR c.id = CAST(:cajaId AS bigint)) " +
            "AND (:maletinId IS NULL OR m.id = CAST(:maletinId AS bigint)) " +
            "AND (:cajeroId IS NULL OR c.usuario_id = CAST(:cajeroId AS bigint)) " +
            "AND (:verificado IS NULL OR c.verificado = :verificado) " +
            "AND ((:fechaInicio IS NULL OR :fechaFin IS NULL) OR c.creado_en BETWEEN :fechaInicio AND :fechaFin) " +
            "AND (:estado IS NULL OR c.estado = CAST(:estado AS TEXT)) " +
            "AND ( :difEstado IS NULL " +
            "      OR (:difEstado = 'CON_DIFERENCIA'  AND ca.total_apertura IS NOT NULL AND cc.total_cierre IS NOT NULL AND ca.total_apertura <> cc.total_cierre) " +
            "      OR (:difEstado = 'SIN_DIFERENCIA' AND ca.total_apertura IS NOT NULL AND cc.total_cierre IS NOT NULL AND ca.total_apertura = cc.total_cierre) " +
            "      OR (:difEstado = 'SIN_DATOS'      AND (ca.total_apertura IS NULL OR cc.total_cierre IS NULL)) )",
            nativeQuery = true)
    Page<PdvCaja> findAllForAnalisisDiferenciasNative(Long cajaId, String estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, String difEstado, Pageable pageable);

}