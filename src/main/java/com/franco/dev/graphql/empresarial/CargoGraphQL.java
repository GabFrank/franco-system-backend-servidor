package com.franco.dev.graphql.empresarial;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.empresarial.input.CargoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.CargoService;
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
public class CargoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CargoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Cargo> cargo(Long id) {return service.findById(id);}

    public List<Cargo> cargos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Cargo> cargosSearch(String texto){
        return service.findByAll(texto);
    }


    public Cargo saveCargo(CargoInput input){
        ModelMapper m = new ModelMapper();
        Cargo e = m.map(input, Cargo.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setSupervisadoPor(service.findById(input.getSupervisadoPorId()).orElse(null));
        e = service.save(e);
        multiTenantService.compartir(null, (Cargo s) -> service.save(s), e);
        return e;
    }

    public Boolean deleteCargo(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countCargo(){
        return service.count();
    }


}
