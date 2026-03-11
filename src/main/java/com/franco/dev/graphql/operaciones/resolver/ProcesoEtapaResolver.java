package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcesoEtapaResolver implements GraphQLResolver<ProcesoEtapa> {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private UsuarioService usuarioService;

    public Pedido pedido(ProcesoEtapa procesoEtapa) {
        return procesoEtapa.getPedido();
    }

    public Usuario usuarioInicio(ProcesoEtapa procesoEtapa) {
        return procesoEtapa.getUsuarioInicio();
    }
} 