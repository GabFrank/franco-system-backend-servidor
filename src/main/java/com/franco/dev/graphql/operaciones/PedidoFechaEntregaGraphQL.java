package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoFechaEntrega;
import com.franco.dev.graphql.operaciones.input.PedidoFechaEntregaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.PedidoFechaEntregaService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PedidoFechaEntregaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoFechaEntregaService service;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PedidoFechaEntrega> pedidoFechaEntrega(Long id) {
        return service.findById(id);
    }

    public PedidoFechaEntrega savePedidoFechaEntrega(PedidoFechaEntregaInput input) {
        ModelMapper m = new ModelMapper();
        PedidoFechaEntrega e = m.map(input, PedidoFechaEntrega.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getPedidoId() != null) {
            e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        }

        return e;
    }

    public Boolean deletePedidoFechaEntrega(Long id) {
        return service.deleteById(id);
    }

    public Long countPedidoFechaEntrega() {
        return service.count();
    }


}
