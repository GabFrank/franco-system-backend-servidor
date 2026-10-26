package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.CategoriaObservacion;
import com.franco.dev.graphql.operaciones.input.CategoriaObservacionInput;
import com.franco.dev.service.operaciones.CategoriaObservacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.graphql.exceptions.ConstraintViolada;
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
public class CategoriaObservacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;
    @Autowired
    private CategoriaObservacionService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<CategoriaObservacion> categoriaObservacion(Long id) { return service.findById(id);}

    public List<CategoriaObservacion> categoriaObservacionSearch(Long id, String texto) { return service.findByIdOrDesc(id, texto);}
    
    public List<CategoriaObservacion> categoriasObservaciones() { return service.findAll2();}


    public CategoriaObservacion saveCategoriaObservacion(CategoriaObservacionInput input){
        seg.requireSuperusuario(); // pantalla solo ADMIN o sin pantalla que la llame
        ModelMapper m = new ModelMapper();
        CategoriaObservacion e = m.map(input, CategoriaObservacion.class);
        if(input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getCreadoEn() != null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        try {
            return service.save(e);
        } catch (RuntimeException ex) {
            if (ConstraintViolada.en(ex, "categoria_observacion_unique")) throw new GraphQLException("Ya existe una categoria con ese nombre.");
            throw ex;
        }
    }

    public CategoriaObservacion updateCategoriaObservacion(Long id, CategoriaObservacionInput input) {
        ModelMapper m = new ModelMapper();
        CategoriaObservacion p = service.getOne(id);
        p = m.map(input, CategoriaObservacion.class);
        return service.save(p);
    }

    public Boolean deleteCategoriaObservacion(Long id) {
        seg.requireSuperusuario(); // pantalla solo ADMIN o sin pantalla que la llame
        Boolean ok = service.deleteById(id);
        return ok;
    }
}
