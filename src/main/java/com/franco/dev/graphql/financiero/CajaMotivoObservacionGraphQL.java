package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaMotivoObservacion;
import com.franco.dev.graphql.financiero.input.CajaCategoriaObservacionInput;
import com.franco.dev.graphql.financiero.input.CajaMotivoObservacionInput;
import com.franco.dev.service.financiero.CajaMotivoObservacionService;
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
public class CajaMotivoObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CajaMotivoObservacionService service;

    @Autowired
    private CajaSubCategoriaObservacionService cajaSubCategoriaObservacionService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<CajaMotivoObservacion> cajaMotivoObservacion(Long id) {return service.findById(id);}

    public List<CajaMotivoObservacion> cajaMotivoObservaciones() {return service.findAll2();}

    public List<CajaMotivoObservacion> findByCajaMotivoIdOrDesc(Long id, String texto){
        return service.findByCajaMotivoIdOrDesc(id, texto);
    }

    public CajaMotivoObservacion saveCajaMotivoObservacion(CajaMotivoObservacionInput input){
        ModelMapper m = new ModelMapper();
        CajaMotivoObservacion e = m.map(input, CajaMotivoObservacion.class);
        if (input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCajaSubCategoriaObsId()!=null) {
            e.setCajaSubCategoriaObservacion(cajaSubCategoriaObservacionService.findById(input.getCajaSubCategoriaObsId()).orElse(null));
        }
        try {
            e = service.save(e);
        } catch (Exception err) {
            err.printStackTrace();
            if (err.getMessage().contains("caja_motivo_observacion_unique")) {
                throw new GraphQLException("Ya existe un motivo con esa descripción");
            } else {
                throw new GraphQLException("No se pudo guardar");
            }
        }
        return e;
    }

    public CajaMotivoObservacion updateCajaMotivoObservacion(Long id, CajaMotivoObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CajaMotivoObservacion p = service.getOne(id);
        p = m.map(input, CajaMotivoObservacion.class);
        return service.save(p);
    }

    public Boolean deleteCajaMotivoObservacion(Long id){
        Boolean ok = service.deleteById(id);
        return ok;
    }
}