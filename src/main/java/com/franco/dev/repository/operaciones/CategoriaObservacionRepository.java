package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.CategoriaObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoriaObservacionRepository extends HelperRepository<CategoriaObservacion, Long> {

    default Class<CategoriaObservacion> getEntityClass() { return CategoriaObservacion.class;}

    @Query("SELECT co FROM CategoriaObservacion co WHERE co.id = :id OR (:texto IS NOT NULL AND UPPER(co.descripcion) LIKE %:texto%)")
    List<CategoriaObservacion> findByIdOrDesc(@Param("id") Long id, @Param("texto") String texto);



}
