package com.franco.dev.graphql.general;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.graphql.general.input.PaisInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
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
public class PaisGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PaisService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Pais> pais(Long id) {
        return service.findById(id);
    }

    public List<Pais> paises(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Pais> paisesSearch(String texto) {
        return service.findByAll(texto);
    }


    public Pais savePais(PaisInput input) {
        ModelMapper m = new ModelMapper();
        Pais e = m.map(input, Pais.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.PAIS);
        multiTenantService.compartir(null, (Pais s) -> service.save(s), e);
        return e;
    }

    public Boolean deletePais(Long id) {
        Boolean ok = service.deleteById(id);
//        if (ok) propagacionService.eliminarEntidad(id, TipoEntidad.PAIS);
        return ok;
    }

    public Long countPais() {
        return service.count();
    }


}
