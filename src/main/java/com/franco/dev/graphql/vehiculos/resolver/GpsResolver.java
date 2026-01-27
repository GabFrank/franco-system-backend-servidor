package com.franco.dev.graphql.vehiculos.resolver;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.service.vehiculos.TelemetriaService;
import graphql.kickstart.tools.GraphQLResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class GpsResolver implements GraphQLResolver<Gps> {

    private final TelemetriaService telemetriaService;

    public Optional<Telemetria> ultimaTelemetria(Gps gps) {
        return telemetriaService.findUltimaByDispositivoId(gps.getId());
    }
}
