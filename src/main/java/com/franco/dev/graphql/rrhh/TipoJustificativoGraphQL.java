package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.TipoJustificativo;
import com.franco.dev.graphql.rrhh.input.TipoJustificativoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.TipoJustificativoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TipoJustificativoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoJustificativoService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<TipoJustificativo> tipoJustificativo(Long id) {
        return service.findById(id);
    }

    public List<TipoJustificativo> tiposJustificativo() {
        return service.findAll2();
    }

    public List<TipoJustificativo> tiposJustificativoActivos() {
        return service.findActivos();
    }

    public TipoJustificativo saveTipoJustificativo(TipoJustificativoInput input) {
        TipoJustificativo e = input.getId() != null
                ? service.findById(input.getId()).orElse(new TipoJustificativo())
                : new TipoJustificativo();
        e.setNombre(input.getNombre());
        e.setDescripcion(input.getDescripcion());
        if (input.getEvitaPenalizacion() != null) e.setEvitaPenalizacion(input.getEvitaPenalizacion());
        if (input.getDescuentaSalario() != null) e.setDescuentaSalario(input.getDescuentaSalario());
        if (input.getRequiereDocumento() != null) e.setRequiereDocumento(input.getRequiereDocumento());
        if (input.getActivo() != null) e.setActivo(input.getActivo());
        // generadoPorSistema no se expone al usuario: lo controla el backend para los
        // tipos que emiten otros modulos (VACACION / FERIADO).
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteTipoJustificativo(Long id) {
        TipoJustificativo e = service.findById(id).orElse(null);
        if (e != null && Boolean.TRUE.equals(e.getGeneradoPorSistema())) {
            throw new GraphQLException("No se puede eliminar un tipo generado por el sistema");
        }
        return service.deleteById(id);
    }
}
