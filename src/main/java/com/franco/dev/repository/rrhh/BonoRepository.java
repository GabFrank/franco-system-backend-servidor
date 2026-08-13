package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Bono;
import com.franco.dev.domain.rrhh.enums.BonoTipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BonoRepository extends HelperRepository<Bono, Long> {

    default Class<Bono> getEntityClass() {
        return Bono.class;
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select b from Bono b where " +
            "(:funcionarioId is null or b.funcionario.id = :funcionarioId) and " +
            "(:tipo is null or b.tipo = :tipo) and " +
            "(cast(:desde as date) is null or b.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or b.fecha <= :hasta) " +
            "order by b.fecha desc, b.id desc")
    Page<Bono> findPage(@Param("funcionarioId") Long funcionarioId,
                        @Param("tipo") BonoTipo tipo,
                        @Param("desde") LocalDate desde,
                        @Param("hasta") LocalDate hasta,
                        Pageable pageable);

    List<Bono> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);
}
