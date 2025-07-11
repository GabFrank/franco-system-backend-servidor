package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.CompraItemService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.NotaRecepcionService;
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

    public Double descuento(NotaRecepcion e){
        Double valor = 0.0;
        List<CompraItem> compraItemList = compraItemService.findByNotaRecepcionId(e.getId());
        for(CompraItem item: compraItemList){
            valor += item.getDescuentoUnitario() * item.getCantidad();
        }
        return valor;
    }

    /**
     * Count verified items for a NotaRecepcion
     * TODO: Will be reimplemented when RecepcionMercaderiaItem entity is properly integrated
     * For now, return 0 as verificado field is in step-specific entities
     */
    public Integer cantidadItensVerificadoRecepcionMercaderia(NotaRecepcion p){
        // TODO: Implement using RecepcionMercaderiaItem queries
        return 0;
    }

}
