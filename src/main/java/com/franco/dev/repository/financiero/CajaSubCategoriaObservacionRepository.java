package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CajaSubCategoriaObservacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CajaSubCategoriaObservacionRepository extends HelperRepository<CajaSubCategoriaObservacion, Long> {

    default Class<CajaSubCategoriaObservacion> getEntityClass() { return CajaSubCategoriaObservacion.class; }

    @Query("SELECT cs FROM CajaSubCategoriaObservacion cs " +
            "WHERE cs.id = :id OR (:texto IS NOT NULL AND b" +
            "UPPER(cs.descripcion) LIKE %:texto%)")
    public List<CajaSubCategoriaObservacion> findByCajaSubCategoriaIdOrDesc (@Param("id") Long id, @Param("texto") String texto);
}
