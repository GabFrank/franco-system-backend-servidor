package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.CuentaBancariaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.BancoService;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.PersonaService;
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
public class CuentaBancariaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CuentaBancariaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private BancoService bancoService;

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<CuentaBancaria> cuentaBancaria(Long id) {
        return service.findById(id);
    }

    public List<CuentaBancaria> cuentasBancarias(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }


    public CuentaBancaria saveCuentaBancaria(CuentaBancariaInput input) {
        ModelMapper m = new ModelMapper();
        CuentaBancaria e = m.map(input, CuentaBancaria.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getBancoId() != null) {
            e.setBanco(bancoService.findById(input.getBancoId()).orElse(null));
        }
        if (input.getMonedaId() != null) {
            e.setMoneda(monedaService.findById(input.getMonedaId()).orElse(null));
        }
        if (input.getPersonaId() != null) {
            e.setPersona(personaService.findById(input.getPersonaId()).orElse(null));
        }
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.CUENTA_BANCARIA);
        multiTenantService.compartir(null, (CuentaBancaria s) -> service.save(s), e);
        return e;
    }

    public List<CuentaBancaria> cuentaBancariasSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deleteCuentaBancaria(Long id) {
        Boolean ok = service.deleteById(id);
        if (ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countCuentaBancaria() {
        return service.count();
    }


}
