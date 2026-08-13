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

    /** Hojas restantes en la chequera: rangoHasta − siguienteNumero + 1 (0 si está agotada/nula). */
    public Integer hojasDisponibles(Chequera c) {
        if (c.getRangoHasta() == null) return 0;
        double sig = c.getSiguienteNumero() != null ? c.getSiguienteNumero() : (c.getRangoDesde() != null ? c.getRangoDesde() : 0);
        long libres = (long) Math.floor(c.getRangoHasta() - sig + 1);
        return (int) Math.max(0, libres);
    }
} 