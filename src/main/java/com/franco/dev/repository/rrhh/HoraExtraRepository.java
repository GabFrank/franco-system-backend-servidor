package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.HoraExtra;
import com.franco.dev.domain.rrhh.enums.HoraExtraTipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface HoraExtraRepository extends HelperRepository<HoraExtra, Long> {

    default Class<HoraExtra> getEntityClass() {
        return HoraExtra.class;
    }

    List<HoraExtra> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<HoraExtra> findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<HoraExtra> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);
    java.util.List<com.franco.dev.domain.rrhh.HoraExtra> findByFechaBetweenAndAnuladaFalse(java.time.LocalDate desde, java.time.LocalDate hasta);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select h from HoraExtra h where " +
            "(:funcionarioId is null or h.funcionario.id = :funcionarioId) and " +
            "(cast(:desde as date) is null or h.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or h.fecha <= :hasta) and " +
            "(:tipo is null or h.tipo = :tipo) " +
            "order by h.fecha desc, h.id desc")
    Page<HoraExtra> findPage(@Param("funcionarioId") Long funcionarioId,
                             @Param("desde") LocalDate desde,
                             @Param("hasta") LocalDate hasta,
                             @Param("tipo") HoraExtraTipo tipo,
                             Pageable pageable);
}
