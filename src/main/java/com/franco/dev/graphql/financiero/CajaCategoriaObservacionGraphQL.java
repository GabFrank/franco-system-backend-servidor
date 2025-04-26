package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaCategoriaObservacion;
import com.franco.dev.graphql.financiero.input.CajaCategoriaObservacionInput;
import com.franco.dev.service.financiero.CajaCategoriaObservacionService;
import com.franco.dev.service.operaciones.CategoriaObservacionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class CajaCategoriaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CajaCategoriaObservacionService service;

    @Autowired
    public UsuarioService usuarioService;

    public Optional<CajaCategoriaObservacion> cajaCategoriaObservacion(Long id) { return service.findById(id); }

    public List<CajaCategoriaObservacion> findByIdOrDesc(Long id, String texto) { return service.findByIdOrDesc(id, texto); }

    public List<CajaCategoriaObservacion> cajasCategoriasObservaciones() { return service.findAll2(); }

    public CajaCategoriaObservacion saveCajaCategoriaObservacion(CajaCategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CajaCategoriaObservacion e = m.map(input, CajaCategoriaObservacion.class);
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getCreadoEn() != null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            if (err.getMessage().contains("caja_categoria_observacion_unique")) {
                throw new GraphQLException("Ya existe una categoria con ese nombre.");
            } else {
                throw new GraphQLException("No se pudo guardar");
            }
        }
        return e;
    }

    public CajaCategoriaObservacion updateCajaCategoriaObservacion(Long id, CajaCategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CajaCategoriaObservacion p = service.getOne(id);
        p = m.map(input, CajaCategoriaObservacion.class);
        return service.save(p);
    }

    public Boolean deleteCajaCategoriaObservacion(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
