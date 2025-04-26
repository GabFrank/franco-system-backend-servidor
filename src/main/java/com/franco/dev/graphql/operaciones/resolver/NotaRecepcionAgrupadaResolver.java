package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionAgrupada;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.*;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotaRecepcionAgrupadaResolver implements GraphQLResolver<NotaRecepcionAgrupada> {

    @Autowired
    private NotaRecepcionAgrupadaService service;

    @Autowired
    private NotaRecepcionService notaRecepcionService;
    
    @Autowired
    private SolicitudPagoService solicitudPagoService;

    public Long cantNotas(NotaRecepcionAgrupada e){
        return notaRecepcionService.countByNotaRecepcionAgrupadaId(e.getId());
    }
    
    /**
     * Resolver method to get the SolicitudPago for a NotaRecepcionAgrupada
     * @param e The NotaRecepcionAgrupada entity
     * @return The associated SolicitudPago or null if not found
     */
    public SolicitudPago solicitudPago(NotaRecepcionAgrupada e) {
        if (e.getId() == null) return null;
        return solicitudPagoService.findByTipoAndReferenciaId(TipoSolicitudPago.COMPRA, e.getId());
    }
}
