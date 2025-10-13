package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.LoteDE;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class DocumentoElectronicoResolver implements GraphQLResolver<DocumentoElectronico> {

    public FacturaLegal facturaLegal(DocumentoElectronico e) {
        return e.getFacturaLegal();
    }

    public LoteDE loteDe(DocumentoElectronico e) {
        return e.getLoteDe();
    }
}


