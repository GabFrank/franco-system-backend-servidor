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
import com.franco.dev.utilitarios.Paginacion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class TelemetriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;

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

    public List<Telemetria> telemetriaPorDispositivo(Long dispositivoId, String startDate, String endDate, Integer page,
            Integer size) {
        // #582: siempre paginada, mas reciente primero. Sin fechas antes traia toda la telemetria
        // del dispositivo. El extremo que falta se completa con un limite abierto en vez de mandar
        // null a la consulta de rango.
        LocalDateTime inicio = stringToDate(startDate);
        LocalDateTime fin = stringToDate(endDate);
        return service.findByDispositivoIdAndFechaGpsBetween(dispositivoId,
                inicio != null ? inicio : SIN_INICIO,
                fin != null ? fin : SIN_FIN,
                Paginacion.of(page, size)).getContent();
    }

    private static final LocalDateTime SIN_INICIO = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime SIN_FIN = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    public Telemetria saveTelemetria(TelemetriaInput input) {
        seg.requireSuperusuario(); // pantalla solo ADMIN o sin pantalla que la llame
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
        seg.requireSuperusuario(); // pantalla solo ADMIN o sin pantalla que la llame
        return service.deleteById(id);
    }
}
