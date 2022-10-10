package com.franco.dev.repository.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.repository.HelperRepository;
import com.franco.dev.repository.HelperRepositoryEmbeddedId;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PdvCajaRepository extends HelperRepository<PdvCaja, EmbebedPrimaryKey> {

    default Class<PdvCaja> getEntityClass() {
        return PdvCaja.class;
    }

//    @Query(value = "select * from financiero.pdv_caja ms " +
//            "where ms.sucursal_id = ?1 order by ms.id desc", nativeQuery = true)
    public List<PdvCaja> findBySucursalIdAndCreadoEnBetween(Long id, LocalDateTime inicio, LocalDateTime fin);

    public List<PdvCaja> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

//    @Query(value = "select * from financiero.pdv_caja pc \n" +
//            "where \n" +
//            "(:maletin_id is null or pc.maletin_id = :maletin_id) and \n" +
//            "(:cajero_id is null or pc.usuario_id = :cajero_id) and \n" +
//            "((:fecha_inicio is null and :fecha_fin is null) or pc.creado_en between cast(:fecha_inicio as timestamp) and cast(:fecha_fin as timestamp))\n")
//    public List<PdvCaja> findByAll();

    public PdvCaja findByUsuarioIdAndActivo(Long id, Boolean activo);

    public PdvCaja findByUsuarioIdAndActivoAndSucursalId(Long id, Boolean activo, Long sucId);

    Optional<PdvCaja> findByIdAndSucursalId(Long id, Long sucId);

    Optional<PdvCaja> findFirstByMaletinIdOrderByCreadoEnDesc(Long id);

}