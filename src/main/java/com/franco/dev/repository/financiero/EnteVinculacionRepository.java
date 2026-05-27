package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.EnteVinculacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EnteVinculacionRepository extends HelperRepository<EnteVinculacion, Long> {

    default Class<EnteVinculacion> getEntityClass() {
        return EnteVinculacion.class;
    }

    @Query("select ev from EnteVinculacion ev where ev.ente.id = ?1 order by ev.id desc")
    List<EnteVinculacion> findByEnteId(Long enteId);

    @Query("select ev from EnteVinculacion ev where ev.sucursal.id = ?1 order by ev.id desc")
    List<EnteVinculacion> findBySucursalId(Long sucursalId);

    @Query(value = "select ev from EnteVinculacion ev where " +
            "(cast(:enteId as long) is null or ev.ente.id = :enteId) " +
            "and (cast(:sucursalId as long) is null or ev.sucursal.id = :sucursalId) " +
            "order by ev.id desc",
            countQuery = "select count(ev) from EnteVinculacion ev where " +
            "(cast(:enteId as long) is null or ev.ente.id = :enteId) " +
            "and (cast(:sucursalId as long) is null or ev.sucursal.id = :sucursalId)")
    Page<EnteVinculacion> findAllByEnteIdAndSucursalId(
            @org.springframework.data.repository.query.Param("enteId") Long enteId,
            @org.springframework.data.repository.query.Param("sucursalId") Long sucursalId,
            Pageable pageable);
}
