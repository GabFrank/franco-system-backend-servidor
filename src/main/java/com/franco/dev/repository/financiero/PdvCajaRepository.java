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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, Long> {

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
            "(c.estado = :estado or cast(:estado as com.franco.dev.domain.financiero.enums.PdvCajaEstado) is null) order by c.id")
    public Page<PdvCaja> findAllWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Long sucId, Boolean verificado, Pageable pageable);
}