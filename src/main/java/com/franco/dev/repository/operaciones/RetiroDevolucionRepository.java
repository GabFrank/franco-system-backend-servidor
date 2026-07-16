package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.RetiroDevolucion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RetiroDevolucionRepository extends HelperRepository<RetiroDevolucion, Long> {

    default Class<RetiroDevolucion> getEntityClass() {
        return RetiroDevolucion.class;
    }

    // cast(:param as ...) en el IS NULL: idiom del proyecto para binds nullable.
    @Query("SELECT r FROM RetiroDevolucion r " +
           "WHERE (cast(:proveedorId as long) IS NULL OR r.proveedor.id = :proveedorId) " +
           "AND (cast(:fechaInicio as timestamp) IS NULL OR r.fecha >= :fechaInicio) " +
           "AND (cast(:fechaFin as timestamp) IS NULL OR r.fecha <= :fechaFin) " +
           "ORDER BY r.fecha DESC")
    Page<RetiroDevolucion> findByFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin,
            Pageable pageable
    );
}
