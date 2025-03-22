package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaRecepcionAgrupadaResolver implements GraphQLResolver<NotaRecepcionAgrupada> {

    @Autowired
    private NotaRecepcionAgrupadaService service;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    public Long cantNotas(NotaRecepcionAgrupada e){
        return notaRecepcionService.countByNotaRecepcionAgrupadaId(e.getId());
    }


}
