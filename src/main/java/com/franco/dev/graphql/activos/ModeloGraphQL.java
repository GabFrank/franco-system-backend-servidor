package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Modelo;
import com.franco.dev.graphql.activos.input.ModeloInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.activos.MarcaService;
import com.franco.dev.service.activos.ModeloService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ModeloGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ModeloService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MarcaService marcaService;

    public Optional<Modelo> modelo(Long id) {
        return service.findById(id);
    }

    public List<Modelo> modelos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public CustomPage<Modelo> modeloSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Modelo> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<Modelo> modelosByMarca(Long marcaId, String texto, int page, int size) {
        return service.findByMarcaIdAndTexto(marcaId, texto, page, size).getContent();
    }

    public Modelo saveModelo(ModeloInput input) {
        ModelMapper m = new ModelMapper();
        Modelo e = m.map(input, Modelo.class);
        if (input.getMarcaId() != null)
            e.setMarca(marcaService.findById(input.getMarcaId()).orElse(null));
        if (input.getUsuarioId() != null)
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el modelo: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteModelo(Long id) {
        try {
            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el modelo: " + err.getMessage());
        }
    }

    public Long countModelo() {
        return service.count();
    }
}
