package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.repository.vehiculos.GpsRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class GpsService extends CrudService<Gps, GpsRepository, Long> {

    private final GpsRepository repository;

    @Override
    public GpsRepository getRepository() {
        return repository;
    }

    public Optional<Gps> findByImei(String imei) {
        return repository.findByImei(imei);
    }

    public List<Gps> findByVehiculoId(Long vehiculoId) {
        return repository.findByVehiculoId(vehiculoId);
    }

    public List<Gps> findAllActivos() {
        return repository.findByActivoTrue();
    }

    public List<Gps> search(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return repository.findAllWithVehiculoFetched();
        }
        return repository.searchByTexto(texto.trim().toLowerCase());
    }

    @Transactional
    public void actualizarUltimaPosicion(Long gpsId, Double latitud, Double longitud,
            LocalDateTime fechaGps, Boolean ignicion) {
        try {
            repository.actualizarUltimaPosicion(gpsId, latitud, longitud, fechaGps, ignicion);
            log.debug("Caché de posición actualizada para GPS ID: {}", gpsId);
        } catch (Exception e) {
            log.error("Error actualizando caché de posición para GPS ID {}: {}", gpsId, e.getMessage());
        }
    }

    public List<Gps> findAllConUltimaPosicion() {
        return repository.findAllWithUltimaPosicion();
    }
}
