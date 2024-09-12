package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class FamiliaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FamiliaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Familia> familia(Long id) {return service.findById(id);}

    public List<Familia> familiaSearch(String texto) {return service.findByAll(texto);}

    public Page<Familia> familiaSearch(String texto, int index, int size) {return service.findByAllWithPage(texto, index, size);}


    public List<Familia> familias(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll2();
    }

    public Familia saveFamilia(FamiliaInput input){
        ModelMapper m = new ModelMapper();
        Familia e = m.map(input, Familia.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if(input.getCreadoEn()!=null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        e = service.save(e);
        return e;
    }

    public Familia updateFamilia(Long id, FamiliaInput input){
        ModelMapper m = new ModelMapper();
        Familia p = service.getOne(id);
        p = m.map(input, Familia.class);
        return service.save(p);
    }

    public Boolean deleteFamilia(Long id){
        Boolean ok = service.deleteById(id);
        return ok;

    }

    public Long countFamilia(){
        return service.count();
    }
}
