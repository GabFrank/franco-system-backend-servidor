package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemSucursal;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.operaciones.PedidoItemSucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoItemResolver implements GraphQLResolver<PedidoItem> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private PedidoItemSucursalService pedidoItemSucursalService;

    @Autowired
    private CompraItemService compraItemService;

    public List<PedidoItemSucursal> pedidoItemSucursales(PedidoItem e) {
        return pedidoItemSucursalService.findByPedidoItemId(e.getId());
    }

    public Double valorTotal(PedidoItem p) {
        Double parcial = 0.0;
        if(p.getCancelado() != null && p.getCancelado()) return parcial;
        if (p.getPresentacionRecepcionProducto() != null && p.getAutorizacionRecepcionProducto()) {
            parcial += (p.getPresentacionRecepcionProducto().getCantidad() != null ? p.getPresentacionRecepcionProducto().getCantidad() : 0.0) * (p.getCantidadRecepcionProducto() != null ? p.getCantidadRecepcionProducto() : 0.0) * ((p.getPrecioUnitarioRecepcionProducto() != null ? p.getPrecioUnitarioRecepcionProducto() : 0.0));
        } else if (p.getPresentacionRecepcionNota() != null && p.getAutorizacionRecepcionNota()) {
            parcial += (p.getPresentacionRecepcionNota().getCantidad() != null ? p.getPresentacionRecepcionNota().getCantidad() : 0.0) * (p.getCantidadRecepcionNota() != null ? p.getCantidadRecepcionNota() : 0.0) * ((p.getPrecioUnitarioRecepcionNota() != null ? p.getPrecioUnitarioRecepcionNota() : 0.0));
        } else {
            parcial += (p.getPresentacionCreacion().getCantidad() != null ? p.getPresentacionCreacion().getCantidad() : 0.0) * (p.getCantidadCreacion() != null ? p.getCantidadCreacion() : 0.0) * ((p.getPrecioUnitarioCreacion() != null ? p.getPrecioUnitarioCreacion() : 0.0));
        }
        return parcial;
    }

    public CompraItem compraItem(PedidoItem e) {
        return compraItemService.findByPedidoItemId(e.getId());
    }

//    public TipoConservacion tipoConservacion(Pedido e){ return e.getTipoConservacion(); }
//    private Optional<SubFamilia> subFamilia(Pedido e) {
//        return subFamiliaService.findById(e.getSubFamilia().getId());
//    }

    public Presentacion presentacion(PedidoItem p){
        if (p.getPresentacionRecepcionProducto() != null && p.getAutorizacionRecepcionProducto()) {
            return p.getPresentacionRecepcionProducto();
        } else if (p.getPresentacionRecepcionNota() != null && p.getAutorizacionRecepcionNota()) {
            return p.getPresentacionRecepcionNota();
        } else {
            return p.getPresentacionCreacion();
        }
    }

    public Double cantidad(PedidoItem p){
        if (p.getCantidadRecepcionProducto() != null && p.getAutorizacionRecepcionProducto()) {
            return p.getCantidadRecepcionProducto();
        } else if (p.getCantidadRecepcionNota() != null && p.getAutorizacionRecepcionNota()) {
            return p.getCantidadRecepcionNota();
        } else {
            return p.getCantidadCreacion();
        }
    }

    public Double precioUnitario(PedidoItem p){
        if (p.getPrecioUnitarioRecepcionProducto() != null && p.getAutorizacionRecepcionProducto()) {
            return p.getPrecioUnitarioRecepcionProducto();
        } else if (p.getPrecioUnitarioRecepcionNota() != null && p.getAutorizacionRecepcionNota()) {
            return p.getPrecioUnitarioRecepcionNota();
        } else {
            return p.getPrecioUnitarioCreacion();
        }
    }

}
