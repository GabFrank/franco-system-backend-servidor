package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.BancoInput;
import com.franco.dev.graphql.financiero.input.MovimientoCajaInput;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.MovimientoCajaService;
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
public class MovimientoCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private MovimientoCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<MovimientoCaja> movimientoCaja(Long id, Long sucId) {return service.findById(new EmbebedPrimaryKey(id, sucId));}

    public List<MovimientoCaja> movimientoCajas(int page, int size, Long sucId){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public MovimientoCaja saveMovimientoCaja(MovimientoCajaInput input){
        ModelMapper m = new ModelMapper();
        MovimientoCaja e = m.map(input, MovimientoCaja.class);
        if(input.getId()==null) input.setActivo(true);
        if(input.getUsuarioId()!=null){
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if(input.getMonedaId()!=null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
            e.setCambio(cambioService.findLastByMonedaId(input.getMonedaId()));
        }
        MovimientoCaja movimientoCaja = service.save(e);
        return movimientoCaja;
    }

//    public List<MovimientoCaja> movimientoCajasSearch(String texto){
//        return service.findByAll(texto);
//    }

    public void desactivarByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento tipoMovimiento, Long referencia, Long sucId) {
        List<MovimientoCaja> movimientoCajaList = service.findByTipoMovimientoAndReferencia(tipoMovimiento, referencia, sucId);
        for(MovimientoCaja movimientoCaja: movimientoCajaList){
            if (movimientoCaja != null) {
                movimientoCaja.setActivo(false);
                service.save(movimientoCaja);
            }
        }
    }

    public Boolean deleteMovimientoCaja(Long id, Long sucId){
        return service.deleteById(new EmbebedPrimaryKey(id, sucId));
    }

    public Long countMovimientoCaja(){
        return service.count();
    }


}
