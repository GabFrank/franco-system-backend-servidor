package com.franco.dev.graphql.equipos;

import com.franco.dev.config.multitenant.CustomPage;
import com.franco.dev.config.multitenant.CustomPageImpl;
import com.franco.dev.graphql.equipos.dto.EquipoOutput;
import com.franco.dev.graphql.equipos.input.EquipoInput;
import com.franco.dev.service.equipos.EquipoService;
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
public class EquipoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private EquipoService service;

    public Optional<EquipoOutput> equipo(Long id) {
        return service.findById(id).map(service::aOutput);
    }

    public List<EquipoOutput> equipos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable).stream()
                .map(service::aOutput)
                .collect(Collectors.toList());
    }

    public Long countEquipo() {
        return service.count();
    }

    public List<EquipoOutput> equipoSearch(String texto) {
        return service.buscar(texto);
    }

    public CustomPage<EquipoOutput> equipoSearchPage(String texto, Integer page, Integer size) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 15 : size;

        Pageable pageable = PageRequest.of(p, s);
        Page<EquipoOutput> pageResult = service.buscarConPagina(texto, p, s);
        return new CustomPageImpl<>(pageResult.getContent(), pageable, pageResult.getTotalElements(), null);
    }

    public List<EquipoOutput> equiposByTipoEquipo(Long tipoEquipoId) {
        return service.buscarPorTipoEquipoId(tipoEquipoId);
    }

    public EquipoOutput saveEquipo(EquipoInput input) {
        try {
            return service.guardarDesdeInput(input);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo guardar el equipo: " + err.getMessage());
        }
    }

    public Boolean deleteEquipo(Long id) {
        try {
            return service.deleteById(id);
        } catch (Exception err) {
            err.printStackTrace();
            throw new GraphQLException("No se pudo eliminar el equipo: " + err.getMessage());
        }
    }
}
