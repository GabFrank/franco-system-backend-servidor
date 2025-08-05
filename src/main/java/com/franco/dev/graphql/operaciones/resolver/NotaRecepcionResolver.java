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

    /**
     * Calcula el valor total de una nota de recepción sumando sus ítems
     * @param e La nota de recepción
     * @return El valor total calculado
     */
    public Double valorTotal(NotaRecepcion e){
        try {
            // Obtener todos los ítems de la nota de recepción
            List<com.franco.dev.domain.operaciones.NotaRecepcionItem> items = 
                notaRecepcionItemService.findByNotaRecepcionId(e.getId());
            
            // Calcular el valor total sumando (cantidad * precio unitario) de cada ítem
            Double valorTotal = items.stream()
                .mapToDouble(item -> {
                    Double cantidad = item.getCantidadEnNota() != null ? item.getCantidadEnNota() : 0.0;
                    Double precioUnitario = item.getPrecioUnitarioEnNota() != null ? item.getPrecioUnitarioEnNota() : 0.0;
                    return cantidad * precioUnitario;
                })
                .sum();
            
            System.out.println("=== CÁLCULO VALOR TOTAL NOTA RECEPCIÓN ===");
            System.out.println("NotaRecepcion ID: " + e.getId());
            System.out.println("Cantidad de ítems: " + items.size());
            System.out.println("Valor total calculado: " + valorTotal);
            
            return valorTotal;
        } catch (Exception ex) {
            System.err.println("Error calculando valor total de nota recepción: " + ex.getMessage());
            ex.printStackTrace();
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
