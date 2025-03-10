package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaSubCategoriaObservacion;
import com.franco.dev.graphql.financiero.input.CajaCategoriaObservacionInput;
import com.franco.dev.graphql.financiero.input.CajaSubCategoriaObservacionInput;
import com.franco.dev.service.financiero.CajaCategoriaObservacionService;
import com.franco.dev.service.financiero.CajaSubCategoriaObservacionService;
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
public class CajaSubCategoriaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {
    @Autowired
    private CajaSubCategoriaObservacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CajaCategoriaObservacionService cajaCategoriaObservacionService;

    public Optional<CajaSubCategoriaObservacion> cajaSubCategoriaObservacion(Long id) { return service.findById(id); }

    public List<CajaSubCategoriaObservacion> cajaSubCategoriasObservaciones() { return service.findAll2(); }

    public List<CajaSubCategoriaObservacion> findByCajaSubCategoriaIdOrDesc(Long id, String texto) { return service.findByCajaSubCategoriaIdOrDesc(id, texto); }

    public CajaSubCategoriaObservacion saveCajaSubCategoriaObservacion(CajaSubCategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CajaSubCategoriaObservacion e = m.map(input, CajaSubCategoriaObservacion.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCajaCategoriaObsId() != null) {
            e.setCajaCategoriaObservacion(cajaCategoriaObservacionService.findById(input.getCajaCategoriaObsId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            if (err.getMessage().contains("caja_subcategoria_observacion_unique")) {
                throw new GraphQLException("Ya existe una subcategoria con ese nombre");
            } else {
                throw new GraphQLException("No se pudo guardar");
            }
        }
        return e;
    }

    public CajaSubCategoriaObservacion updateCajaSubCategoriaObservacion(Long id, CajaCategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CajaSubCategoriaObservacion p = service.getOne(id);
        p = m.map(input, CajaSubCategoriaObservacion.class);
        return service.save(p);
    }

    public Boolean deleteCajaSubCategoriaObservacion(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
