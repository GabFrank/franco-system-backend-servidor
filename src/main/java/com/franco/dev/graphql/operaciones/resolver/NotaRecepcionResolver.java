package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.*;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaRecepcionResolver implements GraphQLResolver<NotaRecepcion> {

    @Autowired
    private NotaRecepcionService service;

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
        Double res = service.getRepository().valor(e.getId());
        if(res != null){
            return res;
        } else {
            return 0.0;
        }
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

    public Integer cantidadItensVerificadoRecepcionMercaderia(NotaRecepcion p){
        return pedidoItemService.getRepository().countByNotaRecepcionIdAndVerificadoRecepcionProducto(p.getId(), true);
    }


}
