package com.franco.dev.repository.configuraciones;

import com.franco.dev.domain.configuraciones.ModificacionDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ModificacionDetalleRepository extends HelperRepository<ModificacionDetalle, Long> {

    default Class<ModificacionDetalle> getEntityClass() {
        return ModificacionDetalle.class;
    }

    @Query("SELECT d FROM ModificacionDetalle d WHERE d.modificacionRegistro.id = :modificacionRegistroId " +
            "ORDER BY d.orden ASC")
    List<ModificacionDetalle> findByModificacionRegistroId(@Param("modificacionRegistroId") Long modificacionRegistroId);
}

