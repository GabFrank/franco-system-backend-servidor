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
        for(PedidoItem item: pedidoItemList){
            valor += (item.getPrecioUnitario() - (item.getDescuentoUnitario() != null ? item.getDescuentoUnitario() : 0.0)) * item.getCantidad() * item.getPresentacion().getCantidad();
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
