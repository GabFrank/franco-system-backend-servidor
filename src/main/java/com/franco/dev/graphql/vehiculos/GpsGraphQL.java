package com.franco.dev.graphql.vehiculos;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.graphql.vehiculos.input.GpsInput;
import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.VehiculoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class GpsGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private GpsService service;

    @Autowired
    private VehiculoService vehiculoService;

    public Optional<Gps> gps(Long id) {
        return service.findById(id);
    }

    public List<Gps> gpsList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countGps() {
        return service.count();
    }

    /**
     * Búsqueda de GPS por texto (IMEI, chapa de vehículo, modelo tracker)
     */
    public List<Gps> gpsSearch(String texto) {
        return service.search(texto);
    }

    public Optional<Gps> gpsByImei(String imei) {
        return service.findByImei(imei);
    }

    public List<Gps> gpsByVehiculo(Long vehiculoId) {
        return service.findByVehiculoId(vehiculoId);
    }

    public List<Gps> gpsActivos() {
        return service.findAllConUltimaPosicion();
    }

    public Gps saveGps(GpsInput input) {
        ModelMapper m = new ModelMapper();
        Gps e = m.map(input, Gps.class);
        if (input.getVehiculoId() != null) {
            e.setVehiculo(vehiculoService.findById(input.getVehiculoId()).orElse(null));
        }
        return service.save(e);
    }

    public Boolean deleteGps(Long id) {
        return service.deleteById(id);
    }

    public Boolean enviarComandoGps(Long id, String tipo, String valor) {
        return service.enviarComando(id, tipo, valor);
    }

    public Gps guardarConfigAlertasGps(Long id, Boolean alertaVelocidad, Integer velocidadLimite,
            Boolean alertaVibracion, Boolean alertaBateriaBaja, Boolean alertaAcc) {
        return service.updateConfigAlertas(id, alertaVelocidad, velocidadLimite, alertaVibracion,
                alertaBateriaBaja, alertaAcc);
    }
}
