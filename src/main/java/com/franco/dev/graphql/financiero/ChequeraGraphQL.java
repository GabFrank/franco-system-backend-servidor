package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.service.financiero.ChequeraService;
import com.franco.dev.service.financiero.CuentaBancariaService;
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
public class ChequeraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ChequeraService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CuentaBancariaService cuentaBancariaService;

    public Optional<Chequera> chequera(Long id) {
        return service.findById(id);
    }

    public List<Chequera> chequeras(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Chequera saveChequera(ChequeraInput input) {
        ModelMapper m = new ModelMapper();
        Chequera e = m.map(input, Chequera.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        e = service.save(e);
        return e;
    }

    public List<Chequera> chequerasSearch(String texto) {
        return service.findByAll(texto);
    }

    /** Chequeras de una cuenta bancaria (para ofrecer cheque como forma de pago). soloActivas por default. */
    public List<Chequera> chequerasPorCuenta(Long cuentaBancariaId, Boolean soloActivas) {
        if (Boolean.FALSE.equals(soloActivas)) {
            return service.getRepository().findByCuentaBancariaIdOrderByIdDesc(cuentaBancariaId);
        }
        return service.getRepository().findByCuentaBancariaIdAndEstadoOrderByIdDesc(
                cuentaBancariaId, com.franco.dev.domain.financiero.enums.EstadoChequera.ACTIVA);
    }

    public Boolean deleteChequera(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countChequera() {
        return service.count();
    }
} 