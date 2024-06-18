package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.ConteoMonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
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
public class ConteoMonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConteoMonedaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<ConteoMoneda> conteoMoneda(Long id, Long sucId) {
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) -> service.findById(new EmbebedPrimaryKey(id, sucId)), new EmbebedPrimaryKey(id, sucId));
    }

    public List<ConteoMoneda> conteoMonedas(int page, int size, Long sucId){
        Pageable pageable = PageRequest.of(page,size);
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) ->  service.findAll(pageable), pageable);
    }


    public ConteoMoneda saveConteoMoneda(ConteoMonedaInput input){
        ModelMapper m = new ModelMapper();
        ConteoMoneda e = m.map(input, ConteoMoneda.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        return multiTenantService.compartir("filial"+e.getSucursalId()+"_bkp", (params) ->  service.save(e), e);
    }

    public List<ConteoMoneda> conteoMonedasPorConteoId(Long id, Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) ->  service.findByConteoId(id, sucId), id, sucId);
    }

//    public List<ConteoMoneda> conteoMonedasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteConteoMoneda(Long id, Long sucId){
        return multiTenantService.compartir("filial"+sucId+"_bkp", (params) ->  service.deleteById(new EmbebedPrimaryKey(id, sucId)), new EmbebedPrimaryKey(id, sucId));
    }

    public Long countConteoMoneda(){
        return service.count();
    }

}
