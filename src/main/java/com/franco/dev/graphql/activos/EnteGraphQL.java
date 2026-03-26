package com.franco.dev.graphql.activos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.activos.Ente;
import com.franco.dev.domain.activos.enums.TipoEnte;
import com.franco.dev.graphql.activos.input.EnteInput;
import com.franco.dev.service.activos.EnteService;
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
public class EnteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EnteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModelMapper modelMapper;

    public Optional<Ente> ente(Long id) {
        return service.findById(id);
    }

    public List<Ente> entes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countEnte() {
        return service.count();
    }

    public List<Ente> entesByTipoEnte(TipoEnte tipoEnte) {
        return service.findByTipoEnte(tipoEnte);
    }

    public Ente enteByReferenciaId(TipoEnte tipoEnte, Long referenciaId) {
        return service.findByTipoEnteAndReferenciaId(tipoEnte, referenciaId).orElse(null);
    }

    public CustomPage<Ente> enteSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<Ente> pageResult = service.findAllWithFilters(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public Ente saveEnte(EnteInput input) {
        Ente e = modelMapper.map(input, Ente.class);
        if (input.getTipoEnte() != null) {
            e.setTipoEnte(TipoEnte.valueOf(input.getTipoEnte()));
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el ente: " + err.getMessage());
        }
        return e;
    }

    public Boolean deleteEnte(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el ente: " + err.getMessage());
        }
    }
}
