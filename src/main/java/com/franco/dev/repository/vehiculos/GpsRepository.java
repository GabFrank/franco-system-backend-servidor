package com.franco.dev.repository.vehiculos;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GpsRepository extends HelperRepository<Gps, Long> {

    default Class<Gps> getEntityClass() {
        return Gps.class;
    }

    Optional<Gps> findByImei(String imei);

    List<Gps> findByVehiculoId(Long vehiculoId);
}
