package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ColectaDevolucion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ColectaDevolucionRepository extends HelperRepository<ColectaDevolucion, Long> {

    default Class<ColectaDevolucion> getEntityClass() {
        return ColectaDevolucion.class;
    }

    @Query("SELECT c FROM ColectaDevolucion c " +
           "WHERE (cast(:sucursalOrigenId as long) IS NULL OR c.sucursalOrigen.id = :sucursalOrigenId) " +
           "AND (cast(:sucursalDestinoId as long) IS NULL OR c.sucursalDestino.id = :sucursalDestinoId) " +
           "AND (cast(:fechaInicio as timestamp) IS NULL OR c.fecha >= :fechaInicio) " +
           "AND (cast(:fechaFin as timestamp) IS NULL OR c.fecha <= :fechaFin) " +
           "ORDER BY c.fecha DESC")
    Page<ColectaDevolucion> findByFilters(
            @Param("sucursalOrigenId") Long sucursalOrigenId,
            @Param("sucursalDestinoId") Long sucursalDestinoId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );
}
