package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalEntrega;
import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.graphql.operaciones.input.PedidoSucursalEntregaInput;
import com.franco.dev.graphql.operaciones.input.PedidoSucursalInfluenciaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.PedidoSucursalEntregaService;
import com.franco.dev.service.operaciones.PedidoSucursalInfluenciaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PedidoSucursalEntregaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoSucursalEntregaService service;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PedidoSucursalEntrega> pedidoSucursalEntrega(Long id) {
        return service.findById(id);
    }

    public PedidoSucursalEntrega savePedidoSucursalEntrega(PedidoSucursalEntregaInput input) {
        ModelMapper m = new ModelMapper();
        PedidoSucursalEntrega e = m.map(input, PedidoSucursalEntrega.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getSucursalId() != null) {
            e.setSucursal(sucursalService.findById(input.getSucursalId()).orElse(null));
        }
        if (input.getPedidoId() != null) {
            e.setPedido(pedidoService.findById(input.getPedidoId()).orElse(null));
        }

        return e;
    }

    public Boolean deletePedidoSucursalEntrega(Long id) {
        return service.deleteById(id);
    }

    public Long countPedidoSucursalEntrega() {
        return service.count();
    }


}
