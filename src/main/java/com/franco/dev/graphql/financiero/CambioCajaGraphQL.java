package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CambioCaja;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.CambioCajaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.CambioCajaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.personas.FuncionarioService;
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
public class CambioCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CambioCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private PropagacionService propagacionService;

    public Optional<CambioCaja> cambioCaja(Long id, Long sucId) {return service.findById(new EmbebedPrimaryKey(id, sucId));}

    public List<CambioCaja> cambioCajas(int page, int size, Long sucId){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public CambioCaja saveCambioCaja(CambioCajaInput input){
        ModelMapper m = new ModelMapper();
        CambioCaja e = m.map(input, CambioCaja.class);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getAutorizadoPorId()!=null) e.setAutorizadoPor(funcionarioService.findById(input.getAutorizadoPorId()).orElse(null));
        if(input.getClienteId()!=null) e.setCliente(clienteService.findById(input.getAutorizadoPorId()).orElse(null));

        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.CAMBIO);
        return e;    }

//    public List<CambioCaja> cambioCajasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public Boolean deleteCambioCaja(Long id, Long sucId){
        Boolean ok = service.deleteById(new EmbebedPrimaryKey(id, sucId));
//        if(ok) propagacionService.eliminarEntidad(id, TipoEntidad.CAMBIO);
        return ok;
    }

    public Long countCambioCaja(){
        return service.count();
    }


}
