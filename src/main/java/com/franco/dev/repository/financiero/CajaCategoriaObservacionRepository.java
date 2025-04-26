package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaCategoriaObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CajaCategoriaObservacionRepository extends HelperRepository<CajaCategoriaObservacion, Long> {

    default Class<CajaCategoriaObservacion> getEntityClass() { return CajaCategoriaObservacion.class; }

    @Query("SELECT cc FROM CajaCategoriaObservacion cc " +
            "WHERE cc.id = :id OR " +
            "(:texto IS NOT NULL AND UPPER(cc.descripcion) LIKE %:texto%)")
    List<CajaCategoriaObservacion> findByIdOrDesc(@Param("id") Long id, @Param("texto") String texto);
}
