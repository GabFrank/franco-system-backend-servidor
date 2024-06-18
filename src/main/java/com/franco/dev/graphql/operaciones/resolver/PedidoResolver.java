package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.enums.TipoConservacion;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.SubFamiliaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoResolver implements GraphQLResolver<Pedido> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    @Autowired
    private CompraService compraService;

    @Autowired
    private PedidoFechaEntregaService pedidoFechaEntregaService;

    @Autowired
    private PedidoSucursalEntregaService pedidoSucursalEntregaService;

    @Autowired
    private PedidoSucursalInfluenciaService pedidoSucursalInfluenciaService;

    public Optional<Usuario> usuario(Pedido e){
        return usuarioService.findById(e.getUsuario().getId());
    }

    public Double valorTotal(Pedido e) {
        List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(e.getId());
        Double total = 0.0;
        for(PedidoItem p: pedidoItemList){
            total += (p.getPresentacion().getCantidad() != null ? p.getPresentacion().getCantidad() : 0.0) * (p.getCantidad() != null ? p.getCantidad() : 0.0) * ((p.getPrecioUnitario() != null ? p.getPrecioUnitario() : 0.0));
        }
        return total != null ? total : 0.0;
    }

    public Double descuento(Pedido e) {
        List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(e.getId());
        Double total = 0.0;
        for(PedidoItem p: pedidoItemList){
            total += (p.getPresentacion().getCantidad() != null ? p.getPresentacion().getCantidad() : 0.0) * (p.getCantidad() != null ? p.getCantidad() : 0.0) * ((p.getDescuentoUnitario() != null ? p.getDescuentoUnitario() : 0.0));
        }
        return total != null ? total : 0.0;
    }

    public String nombreProveedor(Pedido e){
        return e.getProveedor().getPersona().getNombre();
    }

    public String nombreUsuario(Pedido e){
        return e.getUsuario().getPersona().getNombre();
    }

//    public TipoConservacion tipoConservacion(Pedido e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Pedido e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

    public List<NotaRecepcion> notaRecepcionList(Pedido e){
        return notaRecepcionService.findByPedidoId(e.getId());
    }

    public Compra compra(Pedido e){
        return compraService.findByPedidoId(e.getId());
    }

    public List<PedidoFechaEntrega> fechaEntregaList(Pedido pedido){
        return pedidoFechaEntregaService.findByPedido(pedido.getId());
    }

    public List<PedidoSucursalEntrega> sucursalEntregaList(Pedido pedido){
        return pedidoSucursalEntregaService.findByPedidoId(pedido.getId());
    }

    public List<PedidoSucursalInfluencia> sucursalInfluenciaList(Pedido pedido){
        return pedidoSucursalInfluenciaService.findByPedidoId(pedido.getId());
    }

    public Integer cantPedidoItem(Pedido e){
        return pedidoItemService.getRepository().countByPedidoId(e.getId());
    }
}
