package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.service.financiero.ChequeraService;
import com.franco.dev.service.financiero.CuentaBancariaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
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

    @Autowired
    private TesoreriaSecurityService seg;

    public Optional<Chequera> chequera(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Chequera> chequeras(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Chequera saveChequera(ChequeraInput input) {
        seg.requireGestionar();
        ModelMapper m = new ModelMapper();
        Chequera e = m.map(input, Chequera.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getCuentaBancariaId() != null) {
            e.setCuentaBancaria(cuentaBancariaService.findById(input.getCuentaBancariaId()).orElse(null));
        }
        // Update: preservar campos que el input no envia (el save es un merge y los
        // dejaria en null): creado_en, fecha_retiro y usuario creador.
        if (input.getId() != null) {
            Chequera existente = service.findById(input.getId()).orElse(null);
            if (existente != null) {
                if (e.getCreadoEn() == null) e.setCreadoEn(existente.getCreadoEn());
                if (e.getFechaRetiro() == null) e.setFechaRetiro(existente.getFechaRetiro());
                if (e.getUsuario() == null) e.setUsuario(existente.getUsuario());
            }
        }
        e = service.save(e);
        return e;
    }

    public List<Chequera> chequerasSearch(String texto) {
        seg.requireVer();
        return service.findByAll(texto);
    }

    /** Chequeras de una cuenta bancaria (para ofrecer cheque como forma de pago). soloActivas por default. */
    public List<Chequera> chequerasPorCuenta(Long cuentaBancariaId, Boolean soloActivas) {
        seg.requireVer();
        if (Boolean.FALSE.equals(soloActivas)) {
            return service.getRepository().findByCuentaBancariaIdOrderByIdDesc(cuentaBancariaId);
        }
        return service.getRepository().findByCuentaBancariaIdAndEstadoOrderByIdDesc(
                cuentaBancariaId, com.franco.dev.domain.financiero.enums.EstadoChequera.ACTIVA);
    }

    public Boolean deleteChequera(Long id) {
        seg.requireGestionar();
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countChequera() {
        seg.requireVer();
        return service.count();
    }
} 