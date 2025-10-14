package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.domain.financiero.enums.EstadoLoteDE;
import com.franco.dev.graphql.financiero.input.LoteDEInput;
import com.franco.dev.service.financiero.LoteDEService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

import javax.transaction.Transactional;

@Component
public class LoteDEGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LoteDEService service;

    @Autowired
    private UsuarioService usuarioService;

    public Page<LoteDE> loteDes(int page, int size, EstadoLoteDE estado, String fechaInicio, String fechaFin) {
        return service.findByEstadoOrFechaProcesadoBetween(estado, fechaInicio, fechaFin, page, size);
    }

    public List<LoteDE> loteDesPaginado(int page, int size) {
        return service.findAll(page, size);
    }

    public LoteDE loteDe(Long id) {
        return service.findById(id).orElse(null);
    }

    @Transactional
    public LoteDE saveLoteDe(LoteDEInput input) {
        ModelMapper m = new ModelMapper();
        LoteDE entity = m.map(input, LoteDE.class);
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return service.save(entity);
    }
}