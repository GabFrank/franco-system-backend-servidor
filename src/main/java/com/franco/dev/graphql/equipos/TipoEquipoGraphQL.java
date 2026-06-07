package com.franco.dev.graphql.equipos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.domain.equipos.TipoEquipo;
import com.franco.dev.graphql.equipos.dto.TipoEquipoOutput;
import com.franco.dev.graphql.equipos.input.TipoEquipoInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.equipos.TipoEquipoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class TipoEquipoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoEquipoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<TipoEquipoOutput> tipoEquipo(Long id) {
        return service.findById(id).map(service::aOutput);
    }

    public List<TipoEquipoOutput> tiposEquipo(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable).stream()
                .map(service::aOutput)
                .collect(Collectors.toList());
    }

    public Long countTipoEquipo() {
        return service.count();
    }

    public List<TipoEquipoOutput> tipoEquipoSearch(String texto) {
        return service.buscar(texto);
    }

    public CustomPage<TipoEquipoOutput> tipoEquipoSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<TipoEquipoOutput> pageResult = service.buscarConPagina(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public TipoEquipoOutput saveTipoEquipo(TipoEquipoInput input) {
        TipoEquipo entity = new TipoEquipo();
        if (input.getId() != null) {
            entity = service.findById(input.getId()).orElse(new TipoEquipo());
        }
        entity.setDescripcion(input.getDescripcion() != null ? input.getDescripcion().toUpperCase() : null);
        if (input.getSucursalId() != null) {
            entity.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        try {
            entity = service.save(entity);
            return service.aOutput(entity);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el tipo de equipo: " + err.getMessage());
        }
    }

    public Boolean deleteTipoEquipo(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el tipo de equipo: " + err.getMessage());
        }
    }
}
