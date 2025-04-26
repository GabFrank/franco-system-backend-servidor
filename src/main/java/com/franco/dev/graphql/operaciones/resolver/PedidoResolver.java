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

    public Optional<Usuario> usuario(Pedido e) {
        return usuarioService.findById(e.getUsuario().getId());
    }

    public Double valorTotal(Pedido e) {
        List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(e.getId());
        Double total = 0.0;
        for (PedidoItem p : pedidoItemList) {
            if(p.getCancelado() != null && p.getCancelado()) continue;
            if (p.getPresentacionRecepcionProducto() != null && (p.getAutorizacionRecepcionProducto() == null || p.getAutorizacionRecepcionProducto())) {
                total += (p.getPresentacionRecepcionProducto().getCantidad() != null ? p.getPresentacionRecepcionProducto().getCantidad() : 0.0) * (p.getCantidadRecepcionProducto() != null ? p.getCantidadRecepcionProducto() : 0.0) * ((p.getPrecioUnitarioRecepcionProducto() != null ? p.getPrecioUnitarioRecepcionProducto() : 0.0));
            } else if (p.getPresentacionRecepcionNota() != null && (p.getAutorizacionRecepcionNota() == null || p.getAutorizacionRecepcionNota())) {
                total += (p.getPresentacionRecepcionNota().getCantidad() != null ? p.getPresentacionRecepcionNota().getCantidad() : 0.0) * (p.getCantidadRecepcionNota() != null ? p.getCantidadRecepcionNota() : 0.0) * ((p.getPrecioUnitarioRecepcionNota() != null ? p.getPrecioUnitarioRecepcionNota() : 0.0));
            } else {
                total += (p.getPresentacionCreacion().getCantidad() != null ? p.getPresentacionCreacion().getCantidad() : 0.0) * (p.getCantidadCreacion() != null ? p.getCantidadCreacion() : 0.0) * ((p.getPrecioUnitarioCreacion() != null ? p.getPrecioUnitarioCreacion() : 0.0));
            }
        }
        return total != null ? total : 0.0;
    }

    public Double descuento(Pedido e) {
        List<PedidoItem> pedidoItemList = pedidoItemService.findByPedidoId(e.getId());
        Double total = 0.0;
        for (PedidoItem p : pedidoItemList) {
            if(p.getCancelado() != null && p.getCancelado()) continue;
            if (p.getPresentacionRecepcionProducto() != null && (p.getAutorizacionRecepcionProducto() == null || p.getAutorizacionRecepcionProducto())) {
                total += (p.getPresentacionRecepcionProducto().getCantidad() != null ? p.getPresentacionRecepcionProducto().getCantidad() : 0.0) * (p.getCantidadRecepcionProducto() != null ? p.getCantidadRecepcionProducto() : 0.0) * ((p.getDescuentoUnitarioRecepcionProducto() != null ? p.getDescuentoUnitarioRecepcionProducto() : 0.0));
            } else if (p.getPresentacionRecepcionNota() != null && (p.getAutorizacionRecepcionNota() == null || p.getAutorizacionRecepcionNota())) {
                total += (p.getPresentacionRecepcionNota().getCantidad() != null ? p.getPresentacionRecepcionNota().getCantidad() : 0.0) * (p.getCantidadRecepcionNota() != null ? p.getCantidadRecepcionNota() : 0.0) * ((p.getDescuentoUnitarioRecepcionNota() != null ? p.getDescuentoUnitarioRecepcionNota() : 0.0));
            } else {
                total += (p.getPresentacionCreacion().getCantidad() != null ? p.getPresentacionCreacion().getCantidad() : 0.0) * (p.getCantidadCreacion() != null ? p.getCantidadCreacion() : 0.0) * ((p.getDescuentoUnitarioCreacion() != null ? p.getDescuentoUnitarioCreacion() : 0.0));
            }
        }
        return total != null ? total : 0.0;
    }

    public String nombreProveedor(Pedido e) {
        return e.getProveedor().getPersona().getNombre();
    }

    public String nombreUsuario(Pedido e) {
        return e.getUsuario().getPersona().getNombre();
    }

//    public TipoConservacion tipoConservacion(Pedido e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Pedido e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

    public List<NotaRecepcion> notaRecepcionList(Pedido e) {
        return notaRecepcionService.findByPedidoId(e.getId());
    }

    public Compra compra(Pedido e) {
        return compraService.findByPedidoId(e.getId());
    }

    public List<PedidoFechaEntrega> fechaEntregaList(Pedido pedido) {
        return pedidoFechaEntregaService.findByPedido(pedido.getId());
    }

    public List<PedidoSucursalEntrega> sucursalEntregaList(Pedido pedido) {
        return pedidoSucursalEntregaService.findByPedidoId(pedido.getId());
    }

    public List<PedidoSucursalInfluencia> sucursalInfluenciaList(Pedido pedido) {
        return pedidoSucursalInfluenciaService.findByPedidoId(pedido.getId());
    }

    public Integer cantPedidoItem(Pedido e) {
        return pedidoItemService.getRepository().countByPedidoId(e.getId());
    }

    public Long cantPedidoItemSinNota(Pedido e) {
        return pedidoItemService.getRepository().countByPedidoIdAndNotaRecepcionIdIsNull(e.getId());
    }

    public Long cantPedidoItemCancelados(Pedido e) {
        return pedidoItemService.getRepository().countByPedidoIdAndCancelado(e.getId(), true);
    }

    public Integer cantNotas(Pedido p){
        return notaRecepcionService.getRepository().countByPedidoId(p.getId());
    }

    public Integer cantNotasPagadas(Pedido p){
        return notaRecepcionService.getRepository().countByPedidoIdAndPagadoTrue(p.getId());
    }

    public Integer cantNotasCanceladas(Pedido p){
        return 0;
    }

    public Boolean pagado(Pedido p){
        return notaRecepcionService.getRepository().areAllNotasPagadasTrue(p.getId());
    }
}
