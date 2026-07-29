package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.graphql.rrhh.input.JustificativoInput;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.FuncionarioDocumentoService;
import com.franco.dev.service.rrhh.JustificativoService;
import com.franco.dev.service.rrhh.TipoJustificativoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;
import static com.franco.dev.utilitarios.DateUtils.stringToLocalDate;

@Component
public class JustificativoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private JustificativoService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private TipoJustificativoService tipoService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioDocumentoService funcionarioDocumentoService;

    public Optional<Justificativo> justificativo(Long id) {
        return service.findById(id);
    }

    public List<Justificativo> justificativosPorFuncionario(Long funcionarioId) {
        return service.findByFuncionarioId(funcionarioId);
    }

    public List<Justificativo> justificativosPorFuncionarioYRango(Long funcionarioId, String desde, String hasta) {
        return service.findByFuncionarioIdAndFechaBetween(
                funcionarioId,
                stringToDate(desde) != null ? stringToDate(desde).toLocalDate() : null,
                stringToDate(hasta) != null ? stringToDate(hasta).toLocalDate() : null);
    }

    /** Padron del SaaS: toda lista paginada y filtrada en el backend. */
    public Page<Justificativo> justificativosPage(int page, int size, Long funcionarioId, String desde,
                                                  String hasta, Long tipoId) {
        return service.findPage(funcionarioId, tipoId, stringToLocalDate(desde), stringToLocalDate(hasta),
                PageRequest.of(page, size));
    }

    public Justificativo saveJustificativo(JustificativoInput input) {
        seg.requireAnyRole(seg.GESTIONAR);
        Justificativo e = input.getId() != null
                ? service.findById(input.getId()).orElse(new Justificativo())
                : new Justificativo();
        if (input.getFuncionarioId() != null)
            e.setFuncionario(funcionarioService.findById(input.getFuncionarioId()).orElse(null));
        if (input.getFecha() != null && stringToDate(input.getFecha()) != null)
            e.setFecha(stringToDate(input.getFecha()).toLocalDate());
        if (input.getTipoId() != null)
            e.setTipo(tipoService.findById(input.getTipoId()).orElse(null));
        e.setJornadaId(input.getJornadaId());
        e.setSucursalId(input.getSucursalId());
        e.setObservacion(input.getObservacion());
        if (input.getDocumentoId() != null) {
            e.setDocumento(funcionarioDocumentoService.findById(input.getDocumentoId()).orElse(null));
        } else {
            e.setDocumento(null);
        }
        if (input.getRegistradoPorId() != null)
            e.setRegistradoPor(usuarioService.findById(input.getRegistradoPorId()).orElse(null));

        // El catalogo define que tipos exigen respaldo (ej. REPOSO MEDICO, DUELO).
        // Sin esta validacion la bandera requiereDocumento no tendria ningun efecto.
        if (e.getTipo() != null && Boolean.TRUE.equals(e.getTipo().getRequiereDocumento())
                && e.getDocumento() == null) {
            throw new GraphQLException("El tipo '" + e.getTipo().getNombre()
                    + "' exige un documento respaldatorio adjunto.");
        }
        return service.save(e);
    }

    public Boolean deleteJustificativo(Long id) {
        seg.requireAnyRole(seg.GESTIONAR);
        return service.deleteById(id);
    }
}
