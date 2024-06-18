package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.CambioInput;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
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
public class CambioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CambioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<Cambio> cambio(Long id) {return service.findById(id);}

    public List<Cambio> cambios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Cambio ultimoCambioPorMonedaId(Long id){
        return service.findLastByMonedaId(id);
    }


    public Cambio saveCambio(CambioInput input, List<Long> sucursalesIdList){
        ModelMapper m = new ModelMapper();
        Cambio e = m.map(input, Cambio.class);
        if(input.getMonedaId()!=null){
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        e = service.save(e);
        if(sucursalesIdList==null){
//            propagacionService.propagarEntidad(e, TipoEntidad.CAMBIO);
            multiTenantService.compartir(null, (Cambio s) -> service.save(s), e);
        } else {
            for(Long id: sucursalesIdList){
                multiTenantService.compartir("filial"+id+"_bkp", (Cambio s) -> service.save(s), e);
            }
        }
        return e;
    }

    public List<Cambio> cambioPorFecha(String start, String end){
        if (end == null){
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Boolean deleteCambio(Long id){
        Boolean ok = service.deleteById(id);
        if(ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;        }

    public Long countCambio(){
        return service.count();
    }


}
