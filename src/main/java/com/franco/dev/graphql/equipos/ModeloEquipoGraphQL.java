package com.franco.dev.graphql.equipos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.equipos.ModeloEquipo;
import com.franco.dev.graphql.equipos.dto.ModeloEquipoOutput;
import com.franco.dev.graphql.equipos.input.ModeloEquipoInput;
import com.franco.dev.service.equipos.MarcaEquipoService;
import com.franco.dev.service.equipos.ModeloEquipoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ModeloEquipoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ModeloEquipoService service;

    @Autowired
    private MarcaEquipoService marcaEquipoService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<ModeloEquipoOutput> modeloEquipo(Long id) {
        return service.findById(id).map(service::aOutput);
    }

    public CustomPage<ModeloEquipoOutput> modeloEquipoSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<ModeloEquipoOutput> pageResult = service.buscarConPagina(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public ModeloEquipoOutput saveModeloEquipo(ModeloEquipoInput input) {
        ModeloEquipo entity = new ModeloEquipo();
        if (input.getId() != null) {
            entity = service.findById(input.getId()).orElse(new ModeloEquipo());
        }
        entity.setDescripcion(input.getDescripcion());
        if (input.getMarcaId() != null) {
            entity.setMarca(marcaEquipoService.findById(input.getMarcaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            entity = service.save(entity);
            return service.aOutput(entity);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el modelo de equipo: " + err.getMessage());
        }
    }

    public Boolean deleteModeloEquipo(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el modelo de equipo: " + err.getMessage());
        }
    }
}
