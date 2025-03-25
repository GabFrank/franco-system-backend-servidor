package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.service.financiero.ChequeService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChequeResolver implements GraphQLResolver<Cheque> {

    @Autowired
    private ChequeService chequeService;

} 