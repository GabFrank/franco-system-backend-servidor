package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.financiero.input.FormaPagoInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.FormaPagoService;
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
public class FormaPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FormaPagoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public Optional<FormaPago> formaPago(Long id) {
        return service.findById(id);
    }

    public List<FormaPago> formasPago(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }


    public FormaPago saveFormaPago(FormaPagoInput input) {
        ModelMapper m = new ModelMapper();
        FormaPago e = m.map(input, FormaPago.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        e = service.save(e);
//        propagacionService.propagarEntidad(e, TipoEntidad.FORMA_DE_PAGO);
        multiTenantService.compartir(null, (FormaPago s) -> service.save(s), e);
        return e;
    }

    public Boolean deleteFormaPago(Long id) {

        Boolean ok = service.deleteById(id);
        if (ok) multiTenantService.compartir(null, (Long s) -> service.deleteById(s), id);
        return ok;
    }

    public Long countFormaPago() {
        return service.count();
    }


}
