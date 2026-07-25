package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.HojaRuta;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.operaciones.input.HojaRutaInput;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.service.operaciones.HojaRutaService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.activos.VehiculoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToDateEndOfDay;

@Component
public class HojaRutaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private HojaRutaService service;

    @Autowired
    private VehiculoService vehiculoService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private TransferenciaService transferenciaService;

    public Optional<HojaRuta> hojaRuta(Long id) {
        return service.findById(id);
    }

    public List<HojaRuta> hojaRutaList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<HojaRuta> hojaRutaPorVehiculo(Long vehiculoId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByVehiculoId(vehiculoId, pageable).getContent();
    }

    public List<HojaRuta> hojaRutaPorChofer(Long choferId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findByChoferId(choferId, pageable).getContent();
    }

    public Optional<HojaRuta> hojaRutaActivaPorVehiculo(Long vehiculoId) {
        return service.findActivaByVehiculoId(vehiculoId);
    }

    public HojaRuta saveHojaRuta(HojaRutaInput input) {
        ModelMapper m = new ModelMapper();
        HojaRuta e = m.map(input, HojaRuta.class);
        // La entidad se persiste con merge: los campos ausentes en el input se guardarian
        // como null. En edicion se cargan los valores actuales para preservarlos, ya que
        // @PrePersist (que asigna estado y creadoEn) solo corre en el alta.
        HojaRuta hojaRutaActual = input.getId() != null ? service.findById(input.getId()).orElse(null) : null;

        if (input.getVehiculoId() != null) {
            e.setVehiculo(vehiculoService.findById(input.getVehiculoId()).orElse(null));
        } else if (hojaRutaActual != null) {
            e.setVehiculo(hojaRutaActual.getVehiculo());
        }
        if (input.getChoferId() != null) {
            e.setChofer(personaService.findById(input.getChoferId()).orElse(null));
        } else if (hojaRutaActual != null) {
            e.setChofer(hojaRutaActual.getChofer());
        }
        if (input.getFechaSalida() != null)
            e.setFechaSalida(stringToDate(input.getFechaSalida()));
        else if (hojaRutaActual != null)
            e.setFechaSalida(hojaRutaActual.getFechaSalida());
        if (input.getFechaLlegada() != null)
            e.setFechaLlegada(stringToDate(input.getFechaLlegada()));
        else if (hojaRutaActual != null)
            e.setFechaLlegada(hojaRutaActual.getFechaLlegada());

        if (hojaRutaActual != null) {
            if (input.getEstado() == null)
                e.setEstado(hojaRutaActual.getEstado());
            if (input.getKmSalida() == null)
                e.setKmSalida(hojaRutaActual.getKmSalida());
            if (input.getKmLlegada() == null)
                e.setKmLlegada(hojaRutaActual.getKmLlegada());
            e.setCreadoEn(hojaRutaActual.getCreadoEn());
        }

        if (input.getAcompanantesIds() != null && !input.getAcompanantesIds().isEmpty()) {
            List<Persona> acompanantes = input.getAcompanantesIds().stream()
                    .map(id -> personaService.findById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            e.setAcompanantes(acompanantes);
        } else {
            e.setAcompanantes(new ArrayList<>());
        }

        return service.save(e);
    }

    public Boolean deleteHojaRuta(Long id) {
        return service.deleteById(id);
    }

    public List<HojaRuta> hojasRutaConEntregas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findHojasRutaConEntregas(pageable).getContent();
    }

    public List<Transferencia> transferenciasPorHojaRuta(Long hojaRutaId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return transferenciaService.getRepository().findByHojaRutaId(hojaRutaId, pageable).getContent();
    }

    public List<HojaRuta> hojaRutaPorFecha(String inicio, String fin) {
        return service.findByFecha(stringToDate(inicio), stringToDateEndOfDay(fin));
    }
}
