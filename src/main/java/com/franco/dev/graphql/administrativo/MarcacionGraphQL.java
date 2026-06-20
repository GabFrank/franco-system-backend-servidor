package com.franco.dev.graphql.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.graphql.administrativo.input.MarcacionInput;
import com.franco.dev.service.administrativo.MarcacionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class MarcacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MarcacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private ImpresionService impresionService;

    @Autowired
    private com.franco.dev.service.administrativo.JornadaService jornadaService;

    public Optional<Marcacion> marcacion(Long id, Long sucursalId) {
        return service.findById(new EmbebedPrimaryKey(id, sucursalId));
    }

    public Page<Marcacion> marcaciones(String fechaInicio, String fechaFin, Integer page, Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        if (fechaInicio != null && fechaFin != null) {
            return service.findByFechaRange(fechaInicio, fechaFin, page, size);
        }
        return service.findAllPaged(page, size);
    }

    public Page<Marcacion> marcacionesPorUsuario(Long usuarioId, String fechaInicio, String fechaFin, Integer page,
            Integer size) {
        if (page == null)
            page = 0;
        if (size == null)
            size = 10;
        if (fechaInicio != null && fechaFin != null) {
            return service.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, page, size);
        }
        return service.findByUsuarioId(usuarioId, page, size);
    }

    public Marcacion saveMarcacion(MarcacionInput marcacion) {
        Marcacion e = new Marcacion();
        if (marcacion.getId() != null && marcacion.getSucursalId() != null) {
            Optional<Marcacion> existing = service
                    .findById(new EmbebedPrimaryKey(marcacion.getId(), marcacion.getSucursalId()));
            if (existing.isPresent()) {
                e = existing.get();
            } else {
                e.setId(marcacion.getId());
                e.setSucursalId(marcacion.getSucursalId());
            }
        } else {
            if (marcacion.getId() != null)
                e.setId(marcacion.getId());
            if (marcacion.getSucursalId() != null)
                e.setSucursalId(marcacion.getSucursalId());
        }
        if (marcacion.getDeviceId() != null)
            e.setDeviceId(marcacion.getDeviceId());
        if (marcacion.getDeviceInfo() != null)
            e.setDeviceInfo(marcacion.getDeviceInfo());
        if (marcacion.getCodigo() != null)
            e.setCodigo(marcacion.getCodigo());
        if (marcacion.getTipo() != null)
            e.setTipo(marcacion.getTipo());

        if (marcacion.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(marcacion.getUsuarioId()).orElse(null));
        }

        if (marcacion.getSucursalEntradaId() != null) {
            e.setSucursalEntrada(sucursalService.findById(marcacion.getSucursalEntradaId()).orElse(null));
        } else if (marcacion.getSucursalId() != null) {
            e.setSucursalEntrada(sucursalService.findById(marcacion.getSucursalId()).orElse(null));
        }

        if (marcacion.getSucursalSalidaId() != null) {
            e.setSucursalSalida(sucursalService.findById(marcacion.getSucursalSalidaId()).orElse(null));
        } else if (marcacion.getSucursalId() != null
                && marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA) {
            e.setSucursalSalida(sucursalService.findById(marcacion.getSucursalId()).orElse(null));
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        if (marcacion.getFechaEntrada() != null) {
            e.setFechaEntrada(LocalDateTime.parse(marcacion.getFechaEntrada(), formatter));
        }
        if (marcacion.getFechaSalida() != null) {
            e.setFechaSalida(LocalDateTime.parse(marcacion.getFechaSalida(), formatter));
        }

        if (marcacion.getEsSalidaAlmuerzo() != null) {
            e.setEsSalidaAlmuerzo(marcacion.getEsSalidaAlmuerzo());
        }

        if (marcacion.getLatitud() != null)
            e.setLatitud(marcacion.getLatitud());
        if (marcacion.getLongitud() != null)
            e.setLongitud(marcacion.getLongitud());
        if (marcacion.getPrecisionGps() != null)
            e.setPrecisionGps(marcacion.getPrecisionGps());
        if (marcacion.getDistanciaSucursalMetros() != null)
            e.setDistanciaSucursalMetros(marcacion.getDistanciaSucursalMetros());

        return service.save(e);
    }

    public Marcacion reprocesarJornadaDeMarcacion(Long id, Long sucursalId) {
        return service.reprocesarJornadaDeMarcacion(id, sucursalId);
    }

    public String imprimirReporteMarcaciones(Long usuarioId, String fechaInicio, String fechaFin,
            Long usuarioResponsableId) {
        com.franco.dev.domain.personas.Usuario usuarioReporte = null;

        if (usuarioResponsableId != null) {
            usuarioReporte = usuarioService.findById(usuarioResponsableId).orElse(null);
        }

        List<com.franco.dev.domain.administrativo.Jornada> jornadaList;
        if (usuarioId != null && fechaInicio != null && fechaFin != null) {
            jornadaList = jornadaService.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin);
        } else if (usuarioId != null) {
            jornadaList = jornadaService.findByUsuarioId(usuarioId);
        } else if (fechaInicio != null && fechaFin != null) {
            jornadaList = jornadaService.findByFechaRange(fechaInicio, fechaFin);
        } else {
            jornadaList = jornadaService.findAll2();
        }

        return impresionService.imprimirMarcaciones(jornadaList, fechaInicio, fechaFin, usuarioReporte);
    }

}
