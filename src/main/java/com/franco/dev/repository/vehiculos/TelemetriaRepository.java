package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelemetriaRepository extends HelperRepository<Telemetria, Long> {

    default Class<Telemetria> getEntityClass() {
        return Telemetria.class;
    }

    List<Telemetria> findByDispositivoId(Long dispositivoId);

    @Query("SELECT t FROM Telemetria t WHERE t.dispositivo.id = :dispositivoId " +
            "AND (:fechaInicio IS NULL OR t.fechaGps >= :fechaInicio) " +
            "AND (:fechaFin IS NULL OR t.fechaGps <= :fechaFin) " +
            "ORDER BY t.fechaGps DESC")
    Page<Telemetria> findByDispositivoIdAndFechaGpsBetween(Long dispositivoId, LocalDateTime fechaInicio,
            LocalDateTime fechaFin, Pageable pageable);
}
