package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.NecesidadItemService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaRecepcionResolver implements GraphQLResolver<NotaRecepcion> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private PersonaService personaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private CompraItemService compraItemService;

    public Double valor(NotaRecepcion e){
        Double valor = 0.0;
        List<PedidoItem> pedidoItemList = pedidoItemService.findByNotaRecepcionId(e.getId());
        for (PedidoItem p : pedidoItemList) {
            if(p.getCancelado() != null && p.getCancelado()) continue;
            if (p.getPresentacionRecepcionProducto() != null && p.getAutorizacionRecepcionProducto()) {
                valor += (p.getPresentacionRecepcionProducto().getCantidad() != null ? p.getPresentacionRecepcionProducto().getCantidad() : 0.0) * (p.getCantidadRecepcionProducto() != null ? p.getCantidadRecepcionProducto() : 0.0) * ((p.getPrecioUnitarioRecepcionProducto() != null ? p.getPrecioUnitarioRecepcionProducto() : 0.0));
            } else if (p.getPresentacionRecepcionNota() != null && p.getAutorizacionRecepcionNota()) {
                valor += (p.getPresentacionRecepcionNota().getCantidad() != null ? p.getPresentacionRecepcionNota().getCantidad() : 0.0) * (p.getCantidadRecepcionNota() != null ? p.getCantidadRecepcionNota() : 0.0) * ((p.getPrecioUnitarioRecepcionNota() != null ? p.getPrecioUnitarioRecepcionNota() : 0.0));
            } else {
                valor += (p.getPresentacionCreacion().getCantidad() != null ? p.getPresentacionCreacion().getCantidad() : 0.0) * (p.getCantidadCreacion() != null ? p.getCantidadCreacion() : 0.0) * ((p.getPrecioUnitarioCreacion() != null ? p.getPrecioUnitarioCreacion() : 0.0));
            }
        }
        return valor;
    }

    public Integer cantidadItens(NotaRecepcion p){
        return pedidoItemService.countByNotaRecepcionId(p.getId());
    }

    public Double descuento(NotaRecepcion e){
        Double valor = 0.0;
        List<CompraItem> compraItemList = compraItemService.findByNotaRecepcionId(e.getId());
        for(CompraItem item: compraItemList){
            valor += item.getDescuentoUnitario() * item.getCantidad();
        }
        return valor;
    }


}
