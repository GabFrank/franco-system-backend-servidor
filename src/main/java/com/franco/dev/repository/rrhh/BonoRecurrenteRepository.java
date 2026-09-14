package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.BonoRecurrente;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BonoRecurrenteRepository extends HelperRepository<BonoRecurrente, Long> {

    default Class<BonoRecurrente> getEntityClass() {
        return BonoRecurrente.class;
    }

    /** Solo los ids: el scheduler itera y llama al service una vez por plantilla. */
    @Query("select b.id from BonoRecurrente b where b.activo = true order by b.id asc")
    List<Long> idsActivos();

    /** Plantillas vigentes de un funcionario. Las apaga el egreso: ver FuncionarioRrhhService. */
    List<BonoRecurrente> findByFuncionarioIdAndActivoTrue(Long funcionarioId);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select b from BonoRecurrente b where " +
            "(:funcionarioId is null or b.funcionario.id = :funcionarioId) and " +
            "(:activo is null or b.activo = :activo) " +
            "order by b.id desc")
    Page<BonoRecurrente> findPage(@Param("funcionarioId") Long funcionarioId,
                                  @Param("activo") Boolean activo,
                                  Pageable pageable);
}
