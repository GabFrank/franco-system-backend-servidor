package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.graphql.operaciones.dto.RechazoNotaRecepcionItemResultadoDTO;
import com.franco.dev.graphql.operaciones.input.DividirPedidoItemInput;
import com.franco.dev.graphql.operaciones.input.RechazarNotaRecepcionItemInput;
import com.franco.dev.service.operaciones.RecepcionDocumentalService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Rechazo y división de ítems de la recepción documental, cada uno en una transacción. */
@Component
public class RecepcionDocumentalGraphQL implements GraphQLMutationResolver {

    @Autowired
    private RecepcionDocumentalService service;

    public RechazoNotaRecepcionItemResultadoDTO rechazarNotaRecepcionItem(RechazarNotaRecepcionItemInput input) {
        return service.rechazar(input);
    }

    public List<NotaRecepcionItem> dividirPedidoItemEnNotas(DividirPedidoItemInput input) {
        return service.dividir(input);
    }
}
