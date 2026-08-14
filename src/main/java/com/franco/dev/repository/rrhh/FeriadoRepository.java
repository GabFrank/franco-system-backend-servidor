package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.Feriado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeriadoRepository extends HelperRepository<Feriado, Long> {

    default Class<Feriado> getEntityClass() {
        return Feriado.class;
    }

    Optional<Feriado> findByFecha(LocalDate fecha);

    List<Feriado> findByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    List<Feriado> findByActivoTrueOrderByFechaAsc();

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    @Query("select f from Feriado f where " +
            "(cast(:desde as date) is null or f.fecha >= :desde) and " +
            "(cast(:hasta as date) is null or f.fecha <= :hasta) and " +
            "(:descripcion is null or upper(f.descripcion) like concat('%', upper(:descripcion), '%')) and " +
            "(cast(:activo as boolean) is null or f.activo = :activo) " +
            "order by f.fecha desc, f.id desc")
    Page<Feriado> findPage(@Param("desde") LocalDate desde,
                          @Param("hasta") LocalDate hasta,
                          @Param("descripcion") String descripcion,
                          @Param("activo") Boolean activo,
                          Pageable pageable);
}
