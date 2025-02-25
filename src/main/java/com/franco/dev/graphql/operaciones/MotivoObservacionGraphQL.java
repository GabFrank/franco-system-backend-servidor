package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MotivoObservacion;
import com.franco.dev.graphql.operaciones.input.MotivoObservacionInput;
import com.franco.dev.service.operaciones.MotivoObservacionService;
import com.franco.dev.service.operaciones.SubcategoriaObservacionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MotivoObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {
    @Autowired
    private MotivoObservacionService service;

    @Autowired
    private SubcategoriaObservacionService subcategoriaObservacionService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<MotivoObservacion> motivoObservacion(Long id) { return service.findById(id); }

    public List<MotivoObservacion> motivosObservaciones() { return service.findAll2(); }

    public List<MotivoObservacion> findByMotivoIdOrDesc(Long id, String texto) { return service.findByMotivoIdOrDesc(id, texto); }

    public MotivoObservacion saveMotivoObservacion(MotivoObservacionInput input) {
        ModelMapper m = new ModelMapper();
        MotivoObservacion e = m.map(input, MotivoObservacion.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getSubcategoriaObservacionId() != null) {
            e.setSubcategoriaObservacion(subcategoriaObservacionService.findById(input.getSubcategoriaObservacionId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            if (err.getMessage().contains("motivo_observacion_unique")) {
                throw new GraphQLException("Ya existe un motivo con esa descripción");
            } else {
                throw new GraphQLException("No se pudo guardar");
            }
        }
        return e;
    }

    public MotivoObservacion updateMotivoObservacion(Long id, MotivoObservacionInput input) {
        ModelMapper m = new ModelMapper();
        MotivoObservacion p = service.getOne(id);
        p = m.map(input, MotivoObservacion.class);
        return service.save(p);
    }

    public Boolean deleteMotivoObservacion(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
