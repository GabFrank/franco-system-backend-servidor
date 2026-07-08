package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.enums.JornadaNovedadTipo;
import com.franco.dev.graphql.rrhh.input.JustificarJornadaInput;
import com.franco.dev.graphql.rrhh.input.PenalizacionInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.JornadaNovedadService;
import com.franco.dev.service.rrhh.PenalizacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class PenalizacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PenalizacionService service;

    @Autowired
    private JornadaNovedadService jornadaNovedadService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Penalizacion> penalizacion(Long id) {
        return service.findById(id);
    }

    public List<Penalizacion> penalizacionesPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Penalizacion> penalizacionesPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    public List<Penalizacion> penalizacionesPorJornada(Long jornadaId, Long sucursalId) {
        return service.findByJornada(jornadaId, sucursalId);
    }

    public Penalizacion savePenalizacion(PenalizacionInput input) {
        Penalizacion e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Penalizacion())
                : new Penalizacion();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setTipo(input.getTipo());
        e.setDescripcion(input.getDescripcion());
        e.setMonto(input.getMonto());
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        if (input.getAutoGenerada() != null) e.setAutoGenerada(input.getAutoGenerada());
        if (input.getAnulada() != null) e.setAnulada(input.getAnulada());
        if (input.getRegistradoPorId() != null)
            e.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        return service.save(e);
    }

    public Penalizacion anularPenalizacion(Long id) {
        return service.anular(id);
    }

    /** Genera las penalizaciones automaticas por tardanza de la fecha dada. Devuelve la cantidad generada. */
    public Integer generarPenalizacionesAuto(String fecha) {
        LocalDate f = stringToDate(fecha) != null ? stringToDate(fecha).toLocalDate() : LocalDate.now().minusDays(1);
        return service.generarPenalizacionesAuto(f);
    }

    /**
     * Justifica una jornada: crea una novedad JUSTIFICADO y anula las
     * penalizaciones auto-generadas de esa jornada.
     */
    public Boolean justificarJornada(JustificarJornadaInput input) {
        JornadaNovedad n = new JornadaNovedad();
        if (input.getFuncionarioId() != null)
            n.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            n.setFecha(stringToDate(input.getFecha()).toLocalDate());
        n.setTipo(JornadaNovedadTipo.JUSTIFICADO);
        n.setJornadaId(input.getJornadaId());
        n.setSucursalId(input.getSucursalId());
        n.setObservacion(input.getObservacion());
        if (input.getRegistradoPorId() != null)
            n.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));
        jornadaNovedadService.save(n);
        service.anularAutoDeJornada(input.getJornadaId(), input.getSucursalId());
        return true;
    }
}
