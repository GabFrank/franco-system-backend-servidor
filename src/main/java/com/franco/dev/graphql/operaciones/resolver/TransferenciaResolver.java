package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TransferenciaResolver implements GraphQLResolver<Transferencia> {

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    public List<TransferenciaItem> transferenciaItemList(Transferencia e){
        return transferenciaItemService.findByTransferenciaId(e.getId());
    }

}
