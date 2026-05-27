package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.Telemetria;
import com.franco.dev.graphql.activos.input.TelemetriaInput;
import com.franco.dev.service.activos.GpsService;
import com.franco.dev.service.activos.TelemetriaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class TelemetriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TelemetriaService service;

    @Autowired
    private GpsService gpsService;

    public Optional<Telemetria> telemetria(Long id) {
        return service.findById(id);
    }

    public List<Telemetria> telemetriaList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Telemetria> telemetriaPorDispositivo(Long dispositivoId, String startDate, String endDate, int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (startDate != null || endDate != null) {
            return service.findByDispositivoIdAndFechaGpsBetween(dispositivoId,
                    startDate != null ? stringToDate(startDate) : null,
                    endDate != null ? stringToDate(endDate) : null,
                    pageable).getContent();
        }
        return service.findByDispositivoId(dispositivoId);
    }

    public Telemetria saveTelemetria(TelemetriaInput input) {
        ModelMapper m = new ModelMapper();
        Telemetria e = m.map(input, Telemetria.class);
        if (input.getDispositivoId() != null) {
            e.setDispositivo(gpsService.findById(input.getDispositivoId()).orElse(null));
        }
        if (input.getFechaGps() != null) {
            e.setFechaGps(stringToDate(input.getFechaGps()));
        }
        return service.save(e);
    }

    public Boolean deleteTelemetria(Long id) {
        return service.deleteById(id);
    }
}
