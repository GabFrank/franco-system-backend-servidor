package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Vacacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VacacionRepository extends HelperRepository<Vacacion, Long> {

    default Class<Vacacion> getEntityClass() {
        return Vacacion.class;
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select v from Vacacion v where " +
            "(:funcionarioId is null or v.funcionario.id = :funcionarioId) and " +
            "(:anio is null or v.anioServicio = :anio) " +
            "order by v.anioServicio desc, v.id desc")
    Page<Vacacion> findPage(@Param("funcionarioId") Long funcionarioId,
                            @Param("anio") Integer anio,
                            Pageable pageable);

    List<Vacacion> findByFuncionarioIdOrderByAnioServicioDesc(Long funcionarioId);

    Optional<Vacacion> findByFuncionarioIdAndAnioServicio(Long funcionarioId, Integer anioServicio);

    List<Vacacion> findByFuncionarioIdAndPrescritaFalse(Long funcionarioId);

    List<Vacacion> findByPrescritaFalse();
}
