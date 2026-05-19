package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Telemetria;
import com.franco.dev.repository.activos.TelemetriaRepository;
import com.franco.dev.service.CrudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class TelemetriaService extends CrudService<Telemetria, TelemetriaRepository, Long> {

    private final TelemetriaRepository repository;

    /**
     * Horas de retención de telemetría antes de ser purgada.
     * Configurable vía propiedad: vehiculos.telemetria.retention-hours
     */
    @Value("${vehiculos.telemetria.retention-hours:168}") // 7 días por defecto
    private long retentionHours;

    public TelemetriaService(TelemetriaRepository repository) {
        this.repository = repository;
    }

    @Override
    public TelemetriaRepository getRepository() {
        return repository;
    }

    public List<Telemetria> findByDispositivoId(Long dispositivoId) {
        return repository.findByDispositivoId(dispositivoId);
    }

    public Page<Telemetria> findByDispositivoIdAndFechaGpsBetween(Long dispositivoId, LocalDateTime fechaInicio,
            LocalDateTime fechaFin, Pageable pageable) {
        return repository.findByDispositivoIdAndFechaGpsBetween(dispositivoId, fechaInicio, fechaFin, pageable);
    }

    public Optional<Telemetria> findUltimaByDispositivoId(Long dispositivoId) {
        return repository.findFirstByDispositivoIdOrderByFechaGpsDesc(dispositivoId);
    }

    /**
     * Tarea programada que limpia periódicamente la tabla de telemetría para evitar
     * crecimiento indefinido.
     *
     * Se ejecuta cada 8 horas y elimina registros cuya fechaServidor es anterior
     * al umbral configurado.
     */
    @Scheduled(cron = "0 0 */8 * * *")
    @Transactional
    public void purgeOldTelemetry() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(retentionHours);
        int deleted = repository.deleteByFechaServidorBefore(threshold);
        if (deleted > 0) {
            log.info("Telemetría purgada: {} registros eliminados anteriores a {}", deleted, threshold);
        } else {
            log.debug("Telemetría purgada: no se encontraron registros anteriores a {}", threshold);
        }
    }
}
