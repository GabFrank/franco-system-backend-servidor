package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.MotivoObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotivoObservacionRepository extends HelperRepository<MotivoObservacion, Long> {

    default Class<MotivoObservacion> getEntityClass() { return MotivoObservacion.class; }

    @Query("SELECT mo FROM MotivoObservacion mo " +
            "WHERE mo.id = :id " +
            "OR (:texto IS NOT NULL AND UPPER(mo.descripcion) LIKE %:texto%)")
    public List<MotivoObservacion> findByMotivoIdOrDesc (@Param("id") Long id, @Param("texto") String texto);
}
