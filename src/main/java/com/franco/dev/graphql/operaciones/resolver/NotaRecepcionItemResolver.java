package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NotaRecepcionItemResolver implements GraphQLResolver<NotaRecepcionItem> {
    
    @Autowired
    private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    
    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;
    
    @Autowired
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;
    
    /**
     * Resolver para el campo distribucionConcluida
     * @param notaRecepcionItem El nota recepcion item
     * @return true si la distribución documental está concluida, false si está pendiente
     */
    public Boolean distribucionConcluida(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null || notaRecepcionItem.getCantidadEnNota() == null) {
            return false;
        }
        
        return notaRecepcionItemDistribucionService.isDistribucionConcluida(
            notaRecepcionItem.getId(), 
            notaRecepcionItem.getCantidadEnNota()
        );
    }

    /**
     * Resolver para el campo cantidadPendiente
     * @param notaRecepcionItem El nota recepcion item
     * @return Cantidad pendiente de recibir físicamente
     */
    public Double cantidadPendiente(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return 0.0;
        }
        
        return notaRecepcionItemService.getCantidadPendiente(notaRecepcionItem.getId());
    }

    /**
     * Resolver para el campo cantidadRecibida
     * @param notaRecepcionItem El nota recepcion item
     * @return Cantidad total recibida físicamente
     */
    public Double cantidadRecibida(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return 0.0;
        }
        
        return recepcionMercaderiaItemService.getCantidadRecibidaTotal(notaRecepcionItem.getId());
    }

    /**
     * Resolver para el campo cantidadRechazada
     * @param notaRecepcionItem El nota recepcion item
     * @return Cantidad total rechazada físicamente
     */
    public Double cantidadRechazada(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return 0.0;
        }
        
        return recepcionMercaderiaItemService.getCantidadRechazadaTotal(notaRecepcionItem.getId());
    }

    /**
     * Resolver para el campo estadoRecepcion
     * @param notaRecepcionItem El nota recepcion item
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    public String estadoRecepcion(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return "PENDIENTE";
        }
        
        return recepcionMercaderiaItemService.getEstadoRecepcion(notaRecepcionItem.getId());
    }

    /**
     * Resolver para el campo recepcionMercaderiaItems
     * @param notaRecepcionItem El nota recepcion item
     * @return Lista de recepciones físicas para este ítem
     */
    public List<RecepcionMercaderiaItem> recepcionMercaderiaItems(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return new ArrayList<>();
        }
        
        return recepcionMercaderiaItemService.findByNotaRecepcionItemId(notaRecepcionItem.getId());
    }

    /**
     * Resolver para el campo notaRecepcionItemDistribuciones
     * @param notaRecepcionItem El nota recepcion item
     * @return Lista de distribuciones del nota recepcion item
     */
    public List<NotaRecepcionItemDistribucion> notaRecepcionItemDistribuciones(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return new ArrayList<>();
        }
        
        return notaRecepcionItemDistribucionService.findByNotaRecepcionItemId(notaRecepcionItem.getId());
    }
} 