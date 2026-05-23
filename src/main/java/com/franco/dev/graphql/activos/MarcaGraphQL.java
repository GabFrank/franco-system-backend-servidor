package com.franco.dev.graphql.activos;

import com.franco.dev.domain.activos.Marca;
import com.franco.dev.graphql.activos.input.MarcaInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.activos.MarcaService;
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
public class MarcaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MarcaService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Marca> marca(Long id) {
        return service.findById(id);
    }

    public List<Marca> marcas(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Marca> marcaSearch(String texto) {
        return service.findByAll(texto);
    }

    public Marca saveMarca(MarcaInput input) {
        ModelMapper m = new ModelMapper();
        Marca e = m.map(input, Marca.class);
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar la marca: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteMarca(Long id) {
        try {
            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la marca: " + err.getMessage());
        }
    }

    public Long countMarca() {
        return service.count();
    }
}
