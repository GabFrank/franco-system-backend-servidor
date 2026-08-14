package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PenalizacionRepository extends HelperRepository<Penalizacion, Long> {

    default Class<Penalizacion> getEntityClass() {
        return Penalizacion.class;
    }

    List<Penalizacion> findByFuncionarioIdOrderByFechaDesc(Long funcionarioId);

    List<Penalizacion> findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(Long funcionarioId, LocalDate desde, LocalDate hasta);

    List<Penalizacion> findByJornadaIdAndSucursalId(Long jornadaId, Long sucursalId);

    List<Penalizacion> findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse(Long jornadaId, Long sucursalId);
    java.util.List<com.franco.dev.domain.rrhh.Penalizacion> findByFechaBetweenAndAnuladaFalse(java.time.LocalDate desde, java.time.LocalDate hasta);

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select p from Penalizacion p where " +
            "(:funcionarioId is null or p.funcionario.id = :funcionarioId) and " +
            "(cast(:desde as date) is null or p.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or p.fecha <= :hasta) and " +
            "(:tipo is null or p.tipo = :tipo) " +
            "order by p.fecha desc, p.id desc")
    Page<Penalizacion> findPage(@Param("funcionarioId") Long funcionarioId,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta,
                                @Param("tipo") PenalizacionTipo tipo,
                                Pageable pageable);
}
