package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ValeRepository extends HelperRepository<Vale, Long> {

    default Class<Vale> getEntityClass() {
        return Vale.class;
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select v from Vale v where " +
            "(:funcionarioId is null or v.funcionario.id = :funcionarioId) and " +
            "(:estado is null or v.estado = :estado) and " +
            "(cast(:desde as date) is null or v.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or v.fecha <= :hasta) " +
            "order by v.fecha desc, v.id desc")
    Page<Vale> findPage(@Param("funcionarioId") Long funcionarioId,
                        @Param("estado") ValeEstado estado,
                        @Param("desde") LocalDate desde,
                        @Param("hasta") LocalDate hasta,
                        Pageable pageable);

    List<Vale> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Vale> findByEstadoOrderByFechaDesc(ValeEstado estado);

    List<Vale> findByFuncionarioIdAndEstado(Long funcionarioId, ValeEstado estado);
}
