package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.service.operaciones.NotaRecepcionService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RecepcionMercaderiaNotaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(RecepcionMercaderiaNotaGraphQL.class);

    @Autowired
    private RecepcionMercaderiaNotaService service;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    @Autowired
    private NotaRecepcionService notaRecepcionService;

    // ===== QUERY OPERATIONS =====

    public Optional<RecepcionMercaderiaNota> recepcionMercaderiaNota(Long id) {
        return service.findById(id);
    }

    public List<RecepcionMercaderiaNota> recepcionMercaderiaNotasPorRecepcion(Long recepcionId) {
        logger.info("Buscando notas asociadas para RecepcionMercaderia ID: {}", recepcionId);
        try {
            return service.findByRecepcionMercaderiaId(recepcionId);
        } catch (Exception ex) {
            logger.error("Error al buscar notas por recepción: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar notas asociadas: " + ex.getMessage());
        }
    }

    public List<RecepcionMercaderiaNota> recepcionMercaderiaNotasPorNota(Long notaId) {
        logger.info("Buscando recepciones asociadas para NotaRecepcion ID: {}", notaId);
        try {
            return service.findByNotaRecepcionId(notaId);
        } catch (Exception ex) {
            logger.error("Error al buscar recepciones por nota: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar recepciones asociadas: " + ex.getMessage());
        }
    }

    public Long countRecepcionMercaderiaNota() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public RecepcionMercaderiaNota asociarNotaARecepcion(Long recepcionId, Long notaRecepcionId) {
        logger.info("=== Asociando NotaRecepcion {} a RecepcionMercaderia {} ===", notaRecepcionId, recepcionId);
        
        try {
            com.franco.dev.domain.operaciones.RecepcionMercaderia recepcion = 
                recepcionMercaderiaService.findById(recepcionId)
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderia no encontrada con ID: " + recepcionId));
            
            RecepcionMercaderiaNota asociacion = service.asociarNotaARecepcion(recepcion, notaRecepcionId);
            
            logger.info("=== Asociación creada con ID: {} ===", asociacion.getId());
            
            return asociacion;
            
        } catch (Exception ex) {
            logger.error("=== ERROR durante asociación ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al asociar nota con recepción: " + ex.getMessage());
        }
    }

    public Boolean desasociarNotaDeRecepcion(Long recepcionId, Long notaRecepcionId) {
        logger.info("=== Desasociando NotaRecepcion {} de RecepcionMercaderia {} ===", notaRecepcionId, recepcionId);
        
        try {
            service.desasociarNotaDeRecepcion(recepcionId, notaRecepcionId);
            
            logger.info("=== Desasociación completada ===");
            
            return true;
            
        } catch (Exception ex) {
            logger.error("=== ERROR durante desasociación ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al desasociar nota de recepción: " + ex.getMessage());
        }
    }

    public Boolean deleteRecepcionMercaderiaNota(Long id) {
        logger.info("Eliminando RecepcionMercaderiaNota con ID: {}", id);
        try {
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar RecepcionMercaderiaNota: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar asociación: " + ex.getMessage());
        }
    }
} 