package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.repository.vehiculos.TelemetriaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class TelemetriaService extends CrudService<Telemetria, TelemetriaRepository, Long> {

    private final TelemetriaRepository repository;

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
}
