package com.franco.dev.graphql.equipos;

import com.franco.dev.domain.equipos.MarcaEquipo;
import com.franco.dev.graphql.equipos.dto.MarcaEquipoOutput;
import com.franco.dev.graphql.equipos.input.MarcaEquipoInput;
import com.franco.dev.service.equipos.MarcaEquipoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MarcaEquipoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MarcaEquipoService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<MarcaEquipoOutput> marcaEquipo(Long id) {
        return service.findById(id).map(service::aOutput);
    }

    public List<MarcaEquipoOutput> marcaEquipoSearch(String texto) {
        return service.buscar(texto).stream().map(service::aOutput).collect(Collectors.toList());
    }

    public MarcaEquipoOutput saveMarcaEquipo(MarcaEquipoInput input) {
        MarcaEquipo entity = new MarcaEquipo();
        if (input.getId() != null) {
            entity = service.findById(input.getId()).orElse(new MarcaEquipo());
        }
        entity.setDescripcion(input.getDescripcion());
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            entity = service.save(entity);
            return service.aOutput(entity);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la marca de equipo: " + err.getMessage());
        }
    }

    public Boolean deleteMarcaEquipo(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la marca de equipo: " + err.getMessage());
        }
    }
}
