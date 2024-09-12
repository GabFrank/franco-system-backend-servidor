package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.graphql.financiero.input.MaletinInput;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MaletinGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MaletinService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Maletin> maletin(Long id, Long sucId) {
        return service.findById(id);
    }

    public List<Maletin> searchMaletin(String texto, Long sucId) {
        return service.searchByAll(texto, sucId);
    }

    public List<Maletin> maletines(int page, int size) {
        return service.findAll2();
    }

    public Maletin saveMaletin(MaletinInput input) {
        ModelMapper m = new ModelMapper();
        Maletin e = m.map(input, Maletin.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return multiTenantService.compartir("filial"+input.getSucursalId(), (params) -> service.save(e), e);
    }

    public Maletin maletinPorDescripcion(String texto) {
        return service.findByDescripcion(texto);
    }

    public Maletin maletinPorDescripcionPorSucursal(String texto, Long sucId) {
//        return propagacionService.maletinPorDescripcionPorSucursal(texto, sucId);
        return null;
    }

    public Boolean deleteMaletin(Long id, Long sucId) {
        return service.deleteById(id);
    }

    public Long countMaletin() {
        return service.count();
    }


}
