package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoRecepcion;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoRecepcionInput;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.SolicitudPagoRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SolicitudPagoRecepcionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudPagoRecepcionGraphQL.class);

    @Autowired
    private SolicitudPagoRecepcionService service;

    @Autowired
    private SolicitudPagoService solicitudPagoService;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    // ===== QUERY OPERATIONS =====

    public Optional<SolicitudPagoRecepcion> solicitudPagoRecepcion(Long id) {
        return service.findById(id);
    }

    public List<SolicitudPagoRecepcion> solicitudPagoRecepcionesPorSolicitud(Long solicitudPagoId) {
        logger.info("Buscando recepciones asociadas para SolicitudPago ID: {}", solicitudPagoId);
        try {
            return service.findBySolicitudPagoId(solicitudPagoId);
        } catch (Exception ex) {
            logger.error("Error al buscar recepciones por solicitud: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar recepciones asociadas: " + ex.getMessage());
        }
    }

    public List<RecepcionMercaderia> recepcionesAsociadasASolicitud(Long solicitudPagoId) {
        logger.info("Buscando recepciones asociadas a SolicitudPago ID: {}", solicitudPagoId);
        try {
            List<SolicitudPagoRecepcion> asociaciones = service.findBySolicitudPagoId(solicitudPagoId);
            return asociaciones.stream()
                .map(SolicitudPagoRecepcion::getRecepcionMercaderia)
                .collect(Collectors.toList());
        } catch (Exception ex) {
            logger.error("Error al buscar recepciones asociadas: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar recepciones asociadas: " + ex.getMessage());
        }
    }

    public List<SolicitudPagoRecepcion> solicitudPagoRecepcionesPorRecepcion(Long recepcionMercaderiaId) {
        logger.info("Buscando solicitudes para RecepcionMercaderia ID: {}", recepcionMercaderiaId);
        try {
            return service.findByRecepcionMercaderiaId(recepcionMercaderiaId);
        } catch (Exception ex) {
            logger.error("Error al buscar solicitudes por recepción: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar solicitudes asociadas: " + ex.getMessage());
        }
    }

    public Long countSolicitudPagoRecepcion() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public SolicitudPagoRecepcion saveSolicitudPagoRecepcion(SolicitudPagoRecepcionInput input) {
        logger.info("=== Starting SolicitudPagoRecepcion save operation ===");
        
        try {
            SolicitudPagoRecepcion entity = new SolicitudPagoRecepcion();
            
            // Map basic fields
            entity.setId(input.getId());
            // Note: montoAsignado field exists in input/schema but not in entity
            
            // Map navigation properties
            if (input.getSolicitudPagoId() != null) {
                entity.setSolicitudPago(solicitudPagoService.findById(input.getSolicitudPagoId())
                    .orElseThrow(() -> new GraphQLException("SolicitudPago no encontrada con ID: " + input.getSolicitudPagoId())));
            }
            
            if (input.getRecepcionMercaderiaId() != null) {
                entity.setRecepcionMercaderia(recepcionMercaderiaService.findById(input.getRecepcionMercaderiaId())
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderia no encontrada con ID: " + input.getRecepcionMercaderiaId())));
            }
            
            logger.info("=== SolicitudPagoRecepcion mapping completed successfully ===");
            
            SolicitudPagoRecepcion savedEntity = service.save(entity);
            
            logger.info("=== SolicitudPagoRecepcion saved with ID: {} ===", savedEntity.getId());
            
            return savedEntity;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during SolicitudPagoRecepcion save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al guardar asociación solicitud-recepción: " + ex.getMessage());
        }
    }

    public Boolean deleteSolicitudPagoRecepcion(Long id) {
        logger.info("Eliminando SolicitudPagoRecepcion con ID: {}", id);
        try {
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar SolicitudPagoRecepcion: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar asociación: " + ex.getMessage());
        }
    }

    // ===== BUSINESS LOGIC OPERATIONS =====
    // Note: Complex operations like crearSolicitudPagoMultipleRecepciones and procesarPagoSolicitud
    // should be implemented in SolicitudPagoService and called from SolicitudPagoGraphQL
} 