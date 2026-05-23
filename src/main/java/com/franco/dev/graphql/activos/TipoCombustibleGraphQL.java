package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.TipoCombustible;
import com.franco.dev.graphql.activos.input.TipoCombustibleInput;
import com.franco.dev.service.activos.TipoCombustibleService;
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
public class TipoCombustibleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoCombustibleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<TipoCombustible> tipoCombustible(Long id) {
        return service.findById(id);
    }

    public List<TipoCombustible> tiposCombustible(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countTipoCombustible() {
        return service.count();
    }

    public List<TipoCombustible> tipoCombustibleSearch(String texto) {
        return service.findByAll(texto);
    }

    public TipoCombustible saveTipoCombustible(TipoCombustibleInput input) {
        TipoCombustible e = modelMapper.map(input, TipoCombustible.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el tipo de combustible: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteTipoCombustible(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el tipo de combustible: " + err.getMessage());
        }
    }
}
