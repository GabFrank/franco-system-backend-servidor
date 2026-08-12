package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.MotivoAveria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MotivoAveriaRepository extends HelperRepository<MotivoAveria, Long> {

    default Class<MotivoAveria> getEntityClass() {
        return MotivoAveria.class;
    }

    @Query("SELECT m FROM MotivoAveria m WHERE m.activo = true ORDER BY m.descripcion ASC")
    List<MotivoAveria> findAllActivos();
}
