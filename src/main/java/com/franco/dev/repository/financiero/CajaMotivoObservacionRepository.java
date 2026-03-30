package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaMotivoObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface CajaMotivoObservacionRepository extends HelperRepository<CajaMotivoObservacion, Long> {

    default Class<CajaMotivoObservacion> getEntityClass() { return CajaMotivoObservacion.class; }

    @Query("SELECT cm FROM CajaMotivoObservacion cm WHERE cm.id = :id OR" +
            " (:texto IS NOT NULL AND UPPER(cm.descripcion) LIKE %:texto%)")
    List<CajaMotivoObservacion> findByCajaMotivoIdOrDesc(@Param("id") Long id, @Param("texto") String texto);
}