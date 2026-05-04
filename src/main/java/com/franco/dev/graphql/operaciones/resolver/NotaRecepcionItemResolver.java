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

import java.time.LocalDateTime;
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
     * Resolver puntual para compatibilizar LocalDate del dominio con scalar Date (LocalDateTime).
     * Se limita a NotaRecepcionItem para evitar impactos globales.
     */
    public LocalDateTime vencimientoEnNota(NotaRecepcionItem notaRecepcionItem) {
        if (notaRecepcionItem == null || notaRecepcionItem.getVencimientoEnNota() == null) {
            return null;
        }
        return notaRecepcionItem.getVencimientoEnNota().atStartOfDay();
    }
    
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
     * @param sucursalesIds Lista opcional de IDs de sucursales para filtrar el cálculo del estado
     * @return Estado de recepción física: PENDIENTE, VERIFICADO, RECHAZADO, PARCIAL
     */
    public String estadoRecepcion(NotaRecepcionItem notaRecepcionItem, java.util.List<Long> sucursalesIds) {
        if (notaRecepcionItem == null || notaRecepcionItem.getId() == null) {
            return "PENDIENTE";
        }
        
        // Si se proporcionan sucursales, filtrar por ellas; si no, calcular para todas
        if (sucursalesIds != null && !sucursalesIds.isEmpty()) {
            return recepcionMercaderiaItemService.getEstadoRecepcion(notaRecepcionItem.getId(), sucursalesIds);
        } else {
            return recepcionMercaderiaItemService.getEstadoRecepcion(notaRecepcionItem.getId());
        }
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