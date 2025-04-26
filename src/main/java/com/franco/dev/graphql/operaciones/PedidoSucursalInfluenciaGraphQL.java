package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoSucursalInfluencia;
import com.franco.dev.graphql.operaciones.input.PedidoSucursalInfluenciaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.PedidoSucursalInfluenciaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.personas.VendedorService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoSucursalInfluenciaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoSucursalInfluenciaService service;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    public Optional<PedidoSucursalInfluencia> pedidoSucursalInfluencia(Long id) {
        return service.findById(id);
    }

    public PedidoSucursalInfluencia savePedidoSucursalInfluencia(PedidoSucursalInfluenciaInput input) {
        ModelMapper m = new ModelMapper();
        PedidoSucursalInfluencia e = m.map(input, PedidoSucursalInfluencia.class);
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

    public Boolean deletePedidoSucursalInfluencia(Long id) {
        return service.deleteById(id);
    }

    public Long countPedidoSucursalInfluencia() {
        return service.count();
    }


}
