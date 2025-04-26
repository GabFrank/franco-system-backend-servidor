package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.graphql.financiero.input.ChequeInput;
import com.franco.dev.service.financiero.ChequeService;
import com.franco.dev.service.financiero.ChequeraService;
import com.franco.dev.service.operaciones.PagoDetalleCuotaService;
import com.franco.dev.service.personas.PersonaService;
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
public class ChequeGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ChequeService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ChequeraService chequeraService;
    
    @Autowired
    private PagoDetalleCuotaService pagoDetalleCuotaService;
    
    @Autowired
    private PersonaService personaService;

    public Optional<Cheque> cheque(Long id) {
        return service.findById(id);
    }

    public List<Cheque> cheques(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }
    
    public List<Cheque> chequesPorChequeraId(Long chequeraId) {
        return service.findByChequeraId(chequeraId);
    }
    
    public Cheque chequePorPagoDetalleCuotaId(Long pagoDetalleCuotaId) {
        return service.findByPagoDetalleCuotaId(pagoDetalleCuotaId);
    }

    public Cheque saveCheque(ChequeInput input) {
        ModelMapper m = new ModelMapper();
        Cheque e = m.map(input, Cheque.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getChequeraId() != null) {
            e.setChequera(chequeraService.findById(input.getChequeraId()).orElse(null));
        }
        if (input.getPagoDetalleCuotaId() != null) {
            e.setPagoDetalleCuota(pagoDetalleCuotaService.findById(input.getPagoDetalleCuotaId()).orElse(null));
        }
        if (input.getFirmanteId() != null) {
            e.setFirmante(personaService.findById(input.getFirmanteId()).orElse(null));
        }
        e = service.save(e);
        return e;
    }

    public List<Cheque> chequesSearch(String texto) {
        return service.findByAll(texto);
    }

    public Boolean deleteCheque(Long id) {
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countCheque() {
        return service.count();
    }
} 