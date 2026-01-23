package com.franco.dev.service.vehiculos;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.repository.vehiculos.GpsRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
}
