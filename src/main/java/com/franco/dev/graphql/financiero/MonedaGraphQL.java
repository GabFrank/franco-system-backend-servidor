package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.dto.PreciosEnMonedaDto;
import com.franco.dev.graphql.financiero.input.MonedaInput;
import com.franco.dev.service.financiero.ConversionPrecioService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MonedaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private MonedaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService paisService;


    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private ConversionPrecioService conversionPrecioService;

    public Optional<Moneda> moneda(Long id) {
        return service.findById(id);
    }

    /** Kiosco de precios de la PWA. Ver ConversionPrecioService. */
    public List<PreciosEnMonedaDto> convertirPreciosMobile(List<Double> montosGs) {
        return conversionPrecioService.convertir(montosGs);
    }

    public List<Moneda> monedas(int page, int size) {
        return service.findAll2();
    }


    /**
     * Reemplazo completo del registro, salvo dos campos que se conservan:
     * <ul>
     *   <li>{@code principal}: la moneda de la empresa no se cambia desde el ABM. Pasarla a otra
     *   moneda cambia la base de todos los modulos y se hace solo con el procedimiento de corte.
     *   Una moneda nueva nace no principal.</li>
     *   <li>{@code codigoIso}, cuando el input no lo trae: un cliente anterior a V258.5 no conoce el
     *   campo y lo borraria al guardar cualquier otro cambio.</li>
     * </ul>
     */
    public Moneda saveMoneda(MonedaInput input) {
        seg.requireGestionar();
        ModelMapper m = new ModelMapper();
        Moneda e = m.map(input, Moneda.class);
        Moneda actual = input.getId() != null ? service.findById(input.getId()).orElse(null) : null;
        e.setPrincipal(actual != null ? actual.getPrincipal() : Boolean.FALSE);
        if (input.getCodigoIso() == null) {
            e.setCodigoIso(actual != null ? actual.getCodigoIso() : null);
        } else {
            String iso = input.getCodigoIso().trim().toUpperCase(java.util.Locale.ROOT);
            if (iso.isEmpty()) {
                e.setCodigoIso(null);
            } else if (!iso.matches("[A-Z]{3}")) {
                throw new graphql.GraphQLException("El codigo ISO tiene que ser de tres letras (PYG, USD, BRL).");
            } else {
                e.setCodigoIso(iso);
            }
        }
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPais(paisService.findById(input.getPaisId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public List<Moneda> monedasSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deleteMoneda(Long id) {
        seg.requireGestionar();
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countMoneda() {
        return service.count();
    }


}
