package com.franco.dev.graphql.productos;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.graphql.productos.input.SubfamiliaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.SubFamiliaService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SubfamiliaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SubFamiliaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FamiliaService familiaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Subfamilia> subfamilia(Long id) {return service.findById(id);}

    public List<Subfamilia> subfamiliaSearch(String texto) {return service.findByDescripcion(texto);}

//    public List<Subfamilia> subfamilias(int page, int size){
//        Pageable pageable = PageRequest.of(page,size);
//        return service.findAll(pageable);
//    }

    public Subfamilia saveSubfamilia(SubfamiliaInput input){
        ModelMapper m = new ModelMapper();
        Subfamilia e = m.map(input, Subfamilia.class);
        if(input.getUsuarioId()!=null){
            e.setUsuarioId(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getFamiliaId()!=null){
            e.setFamilia(familiaService.findById((input.getFamiliaId())).orElse(null));

        }
        if(input.getSubfamiliaId()!=null){
            e.setSubfamilia(service.findById((input.getSubfamiliaId())).orElse(null));
        }
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.SUBFAMILIA);
        multiTenantService.compartir(null, (Subfamilia s) -> service.save(s), e);
        return e;
    }

    public Subfamilia updateSubfamilia(Long id, SubfamiliaInput input){
        ModelMapper m = new ModelMapper();
        Subfamilia p = service.getOne(id);
        p = m.map(input, Subfamilia.class);
        return service.save(p);
    }

    public Boolean deleteSubfamilia(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countSubfamilia(){
        return service.count();
    }

    public List<Subfamilia> subfamilias(int page, int size){
        return service.findBySubfamiliaIsNull();
    }
}
