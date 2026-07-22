package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JustificativoRepository extends HelperRepository<Justificativo, Long> {

    default Class<Justificativo> getEntityClass() {
        return Justificativo.class;
    }

    List<Justificativo> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Justificativo> findByFuncionarioIdAndFecha(Long funcionarioId, LocalDate fecha);

    List<Justificativo> findByFuncionarioIdAndFechaBetweenOrderByFechaAsc(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<Justificativo> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);

    long countByTipoId(Long tipoId);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select j from Justificativo j where " +
            "(:funcionarioId is null or j.funcionario.id = :funcionarioId) and " +
            "(:tipoId is null or j.tipo.id = :tipoId) and " +
            "(cast(:desde as date) is null or j.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or j.fecha <= :hasta) " +
            "order by j.fecha desc, j.id desc")
    Page<Justificativo> findPage(@Param("funcionarioId") Long funcionarioId,
                                 @Param("tipoId") Long tipoId,
                                 @Param("desde") LocalDate desde,
                                 @Param("hasta") LocalDate hasta,
                                 Pageable pageable);
}
