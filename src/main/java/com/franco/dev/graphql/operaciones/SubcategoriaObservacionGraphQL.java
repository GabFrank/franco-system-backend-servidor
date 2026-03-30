package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SubcategoriaObservacion;
import com.franco.dev.graphql.operaciones.input.SubcategoriaObservacionInput;
import com.franco.dev.service.operaciones.CategoriaObservacionService;
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
public class SubcategoriaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {
    @Autowired
    private SubcategoriaObservacionService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CategoriaObservacionService categoriaObservacionService;

    public Optional<SubcategoriaObservacion> subcategoriaObservacion(Long id) { return service.findById(id); }

    public List<SubcategoriaObservacion> subcategoriasObservaciones() { return service.findAll2(); }

    public List<SubcategoriaObservacion> subcategoriaObservacionSearch(Long id, String texto) { return service.findBySubcategoriaIdOrDesc(id, texto); }

    public SubcategoriaObservacion saveSubcategoriaObservacion(SubcategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        SubcategoriaObservacion e = m.map(input, SubcategoriaObservacion.class);
        if (input.getUsuarioId() != null) {
           e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCategoriaObservacionId() != null) {
            e.setCategoriaObservacion(categoriaObservacionService.findById(input.getCategoriaObservacionId()).orElse(null));
        }
       try {
           e = service.save(e);
       } catch (Exception err) {
           err.printStackTrace();
           if (err.getMessage().contains("subcategoria_observacion_unique")) {
               throw new GraphQLException("Ya existe una subcategoria con ese nombre");
           } else {
               throw new GraphQLException("No se pudo guardar");
           }
       }
        return e;
    }

    public SubcategoriaObservacion updateSubcategoriaObservacion(Long id, SubcategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        SubcategoriaObservacion p = service.getOne(id);
        p = m.map(input, SubcategoriaObservacion.class);
        return service.save(p);
    }

    public Boolean deleteSubcategoriaObservacion(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
