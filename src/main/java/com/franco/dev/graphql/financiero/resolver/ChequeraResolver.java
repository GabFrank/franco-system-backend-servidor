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
} 