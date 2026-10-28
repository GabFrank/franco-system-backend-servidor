package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Sorteo;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface SorteoRepository extends HelperRepository<Sorteo, Long> {

    default Class<Sorteo> getEntityClass() {
        return Sorteo.class;
    }

    @Query(value = "select s from Sorteo s " +
            "where (:texto is null or UPPER(s.nombre) like %:texto%) " +
            "and (:soloVigentes = false or (s.activo = true " +
            "     and (s.fechaDesde is null or s.fechaDesde <= :hoy) " +
            "     and (s.fechaHasta is null or s.fechaHasta >= :hoy))) " +
            "order by s.id desc",
            countQuery = "select count(s) from Sorteo s " +
                    "where (:texto is null or UPPER(s.nombre) like %:texto%) " +
                    "and (:soloVigentes = false or (s.activo = true " +
                    "     and (s.fechaDesde is null or s.fechaDesde <= :hoy) " +
                    "     and (s.fechaHasta is null or s.fechaHasta >= :hoy)))")
    Page<Sorteo> filtrar(@Param("texto") String texto,
                         @Param("soloVigentes") boolean soloVigentes,
                         @Param("hoy") LocalDate hoy,
                         Pageable pageable);

    /** Nombre repetido entre sorteos activos (al crear, pasar idExcluido = -1). */
    boolean existsByNombreIgnoreCaseAndActivoTrueAndIdNot(String nombre, Long idExcluido);
}
