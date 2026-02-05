package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MarcacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private final MarcacionService service;
    private final UsuarioService usuarioService;
    private final SucursalService sucursalService;

    public Marcacion marcacion(Long id) {
        return service.findById(id);
    }

    public List<Marcacion> marcaciones(int page, int size) {
        return service.findAll(page, size);
    }

    public List<Marcacion> marcacionesPorUsuario(Long usuarioId, String fechaInicio, String fechaFin) {
        LocalDateTime inicio = null;
        LocalDateTime fin = null;

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        if (fechaInicio != null) {
            inicio = java.time.ZonedDateTime.parse(fechaInicio).toLocalDateTime();
        }
        if (fechaFin != null) {
            fin = java.time.ZonedDateTime.parse(fechaFin).toLocalDateTime();
        }

        return service.findByUsuarioId(usuarioId, inicio, fin);
    }

    public Marcacion saveMarcacion(MarcacionInput input) {
        Marcacion m = new Marcacion();
        if (input.getId() != null) {
            m = service.findById(input.getId());
        }

        m.setTipo(input.getTipo());
        m.setLatitud(input.getLatitud());
        m.setLongitud(input.getLongitud());
        m.setPrecisionGps(input.getPrecisionGps());
        m.setDistanciaSucursalMetros(input.getDistanciaSucursalMetros());
        m.setDeviceId(input.getDeviceId());
        m.setDeviceInfo(input.getDeviceInfo());
        m.setPresencial(input.getPresencial());
        m.setCodigo(input.getCodigo());

        if (input.getUsuarioId() != null) {
            Usuario u = usuarioService.findById(input.getUsuarioId()).orElse(null);
            m.setUsuario(u);
        }

        if (input.getSucursalEntradaId() != null) {
            Sucursal s = sucursalService.findById(input.getSucursalEntradaId()).orElse(null);
            m.setSucursalEntrada(s);
        }

        if (input.getFechaEntrada() != null) {
            m.setFechaEntrada(java.time.ZonedDateTime.parse(input.getFechaEntrada()).toLocalDateTime());
        }

        if (input.getSucursalSalidaId() != null) {
            Sucursal s = sucursalService.findById(input.getSucursalSalidaId()).orElse(null);
            m.setSucursalSalida(s);
        }

        if (input.getFechaSalida() != null) {
            m.setFechaSalida(java.time.ZonedDateTime.parse(input.getFechaSalida()).toLocalDateTime());
        }

        return service.save(m);
    }
}
