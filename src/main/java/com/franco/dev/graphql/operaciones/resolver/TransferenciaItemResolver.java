package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.dto.TransferenciaItemLoteDto;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransferenciaItemResolver implements GraphQLResolver<TransferenciaItem> {

    @Autowired
    private TransferenciaItemLoteService transferenciaItemLoteService;

    /**
     * Lotes que el operador eligio a mano para este item.
     *
     * Se resuelve como campo y no como asociacion de la entidad a proposito: {@code ModelMapper}
     * mapea {@link com.franco.dev.graphql.operaciones.input.TransferenciaItemInput} sobre la
     * entidad en cada guardado, y una coleccion mapeada ahi se pisaria sola. Ademas, solo se
     * consulta cuando la pantalla pide el campo.
     */
    public List<TransferenciaItemLoteDto> lotesAsignados(TransferenciaItem transferenciaItem) {
        if (transferenciaItem == null || transferenciaItem.getId() == null) {
            return new ArrayList<>();
        }
        return transferenciaItemLoteService.asignacionesDtoPorItem(transferenciaItem.getId());
    }
}
