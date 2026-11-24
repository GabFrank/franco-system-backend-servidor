package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.service.financiero.ChequeService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChequeraResolver implements GraphQLResolver<Chequera> {

    @Autowired
    private ChequeService chequeService;

    public List<Cheque> cheques(Chequera chequera) {
        return chequeService.findByChequeraId(chequera.getId());
    }

    @Autowired
    private com.franco.dev.service.financiero.ChequeGestionService chequeGestionService;

    /**
     * Hojas restantes: desde el próximo número hasta el fin del rango, sin los inutilizados ni los que ya tienen
     * cheque (issue #603). 0 sin rango.
     */
    public Integer hojasDisponibles(Chequera c) {
        if (c.getRangoHasta() == null) return 0;
        return (int) Math.min(Integer.MAX_VALUE, chequeGestionService.hojasLibres(c));
    }
} 