package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.TipoMueble;
import com.franco.dev.graphql.activos.input.TipoMuebleInput;
import com.franco.dev.service.activos.FamiliaMuebleService;
import com.franco.dev.service.activos.TipoMuebleService;
import com.franco.dev.service.personas.UsuarioService;
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
public class TipoMuebleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoMuebleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FamiliaMuebleService familiaMuebleService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<TipoMueble> tipoMueble(Long id) {
        return service.findById(id);
    }

    public List<TipoMueble> tiposMueble(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countTipoMueble() {
        return service.count();
    }

    public CustomPage<TipoMueble> tipoMuebleSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<TipoMueble> pageResult = service.findByAllWithPage(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<TipoMueble> tiposMuebleByFamilia(Long familiaMuebleId) {
        return service.findByFamiliaMuebleId(familiaMuebleId);
    }

    public TipoMueble saveTipoMueble(TipoMuebleInput input) {
        TipoMueble e = modelMapper.map(input, TipoMueble.class);
        if (input.getFamiliaMuebleId() != null) {
            e.setFamiliaMueble(familiaMuebleService.findById(input.getFamiliaMuebleId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el tipo de mueble: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteTipoMueble(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el tipo de mueble: " + err.getMessage());
        }
    }
}
