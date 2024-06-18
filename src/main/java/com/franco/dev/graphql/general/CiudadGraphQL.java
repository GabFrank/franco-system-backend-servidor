package com.franco.dev.graphql.general;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.general.input.CiudadInput;
import com.franco.dev.graphql.general.input.PaisInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
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
public class CiudadGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CiudadService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService ciudadService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Ciudad> ciudad(Long id) {return service.findById(id);}

    public List<Ciudad> ciudades(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Ciudad> ciudadesSearch(String texto){
        return service.findByAll(texto);
    }


    public Ciudad saveCiudad(CiudadInput input){
        ModelMapper m = new ModelMapper();
        Ciudad e = m.map(input, Ciudad.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPais(ciudadService.findById(input.getPaisId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.CIUDAD);
        multiTenantService.compartir(null, (Ciudad s) -> service.save(s), e);
        return e;    }

    public Boolean deleteCiudad(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countCiudad(){
        return service.count();
    }


}
