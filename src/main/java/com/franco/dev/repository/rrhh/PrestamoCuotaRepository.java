package com.franco.dev.repository.rrhh;

import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrestamoCuotaRepository extends HelperRepository<PrestamoCuota, Long> {

    default Class<PrestamoCuota> getEntityClass() {
        return PrestamoCuota.class;
    }

    List<PrestamoCuota> findByPrestamoIdOrderByNumeroAsc(Long prestamoId);

    List<PrestamoCuota> findByEstadoAndFechaVencimientoBefore(PrestamoCuotaEstado estado, LocalDate fecha);
    java.util.List<com.franco.dev.domain.rrhh.PrestamoCuota> findByEstado(com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado estado);

    /**
     * Toma la cuota con lock pesimista para el cobro. Serializa dos cobros de la misma cuota: el segundo
     * espera el commit del primero y lee lo que ese dejo (issue #299).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PrestamoCuota c where c.id = :id")
    Optional<PrestamoCuota> lockById(@Param("id") Long id);

    /**
     * Pasa a VENCIDA en una sola sentencia, tocando solo el estado. Cargar y guardar la entidad entera
     * pisaba el monto_pagado de un cobro que commiteaba en el medio; aca PostgreSQL vuelve a evaluar el
     * WHERE despues de esperar el lock, asi que una cuota recien pagada queda afuera.
     */
    @Modifying
    @Query("update PrestamoCuota c set c.estado = :vencida "
            + "where c.estado in :estados and c.fechaVencimiento < :fecha")
    int marcarVencidas(@Param("vencida") PrestamoCuotaEstado vencida,
                       @Param("estados") Collection<PrestamoCuotaEstado> estados,
                       @Param("fecha") LocalDate fecha);
}
