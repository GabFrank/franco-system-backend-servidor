package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.LoteDE;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoteDEResolver implements GraphQLResolver<LoteDE> {

    @Autowired
    private DocumentoElectronicoService documentoElectronicoService;

    public List<DocumentoElectronico> documentosElectronicos(LoteDE lote) {
        return documentoElectronicoService.findByLoteDeIdList(lote.getId());
    }
}


