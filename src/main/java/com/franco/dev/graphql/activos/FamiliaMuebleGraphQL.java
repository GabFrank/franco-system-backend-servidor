package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.FamiliaMueble;
import com.franco.dev.graphql.activos.input.FamiliaMuebleInput;
import com.franco.dev.service.activos.FamiliaMuebleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
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
public class FamiliaMuebleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FamiliaMuebleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<FamiliaMueble> familiaMueble(Long id) {
        return service.findById(id);
    }

    public List<FamiliaMueble> familiasMueble(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countFamiliaMueble() {
        return service.count();
    }

    public List<FamiliaMueble> familiaMuebleSearch(String texto) {
        return service.findByAll(texto);
    }

    public FamiliaMueble saveFamiliaMueble(FamiliaMuebleInput input) {
        FamiliaMueble e = modelMapper.map(input, FamiliaMueble.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la familia de mueble: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteFamiliaMueble(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la familia de mueble: " + err.getMessage());
        }
    }
}
