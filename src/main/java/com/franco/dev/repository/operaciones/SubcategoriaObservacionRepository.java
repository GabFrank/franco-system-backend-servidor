package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SubcategoriaObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubcategoriaObservacionRepository extends HelperRepository<SubcategoriaObservacion, Long> {

    default Class<SubcategoriaObservacion> getEntityClass() { return SubcategoriaObservacion.class; }

    @Query("SELECT so FROM SubcategoriaObservacion so" +
            " WHERE so.id = :id " +
            "OR (:texto IS NOT NULL AND UPPER(so.descripcion) LIKE %:texto%)")
    public List<SubcategoriaObservacion> findBySubcategoriaIdOrDesc (@Param("id") Long id, @Param("texto") String texto);
}
