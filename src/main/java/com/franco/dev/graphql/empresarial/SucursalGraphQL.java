package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.graphql.empresarial.input.SucursalInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.security.Unsecured;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SucursalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private Environment env;

    @Autowired
    private SucursalService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<Sucursal> sucursal(Long id) {return service.findById(id);}

    public List<Sucursal> sucursales(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Sucursal> sucursalesSearch(String texto){
        return service.findByAll(texto);
    }

    public Sucursal sucursalActual(){
        return service.findById(Long.valueOf(env.getProperty("sucursalId"))).orElse(null);
    }

    public Sucursal saveSucursal(SucursalInput input){
        ModelMapper m = new ModelMapper();
        Sucursal e = m.map(input, Sucursal.class);
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.SUCURSAL);
        return e;
    }

    public Boolean deleteSucursal(Long id){
        Boolean ok = service.deleteById(id);
//        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.SUCURSAL);
        return ok;
    }

    public Long countSucursal(){
        return service.count();
    }


}
