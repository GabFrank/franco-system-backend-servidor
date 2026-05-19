package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Telemetria;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    Optional<Telemetria> findFirstByDispositivoIdOrderByFechaGpsDesc(Long dispositivoId);

    @Modifying
    @Query("DELETE FROM Telemetria t WHERE t.fechaServidor < :threshold")
    int deleteByFechaServidorBefore(LocalDateTime threshold);
}
