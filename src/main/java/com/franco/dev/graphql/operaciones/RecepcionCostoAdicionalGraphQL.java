package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionCostoAdicional;
import com.franco.dev.graphql.operaciones.input.RecepcionCostoAdicionalInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.RecepcionCostoAdicionalService;
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
public class RecepcionCostoAdicionalGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(RecepcionCostoAdicionalGraphQL.class);

    @Autowired
    private RecepcionCostoAdicionalService service;

    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    @Autowired
    private MonedaService monedaService;

    // ===== QUERY OPERATIONS =====

    public Optional<RecepcionCostoAdicional> recepcionCostoAdicional(Long id) {
        return service.findById(id);
    }

    public List<RecepcionCostoAdicional> recepcionCostoAdicionalPorRecepcion(Long recepcionId) {
        logger.info("Buscando costos adicionales para RecepcionMercaderia ID: {}", recepcionId);
        try {
            return service.findByRecepcionMercaderiaId(recepcionId);
        } catch (Exception ex) {
            logger.error("Error al buscar costos adicionales por recepción: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar costos adicionales: " + ex.getMessage());
        }
    }

    public Long countRecepcionCostoAdicional() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public RecepcionCostoAdicional saveRecepcionCostoAdicional(RecepcionCostoAdicionalInput input) {
        logger.info("=== Starting RecepcionCostoAdicional save operation ===");
        
        try {
            RecepcionCostoAdicional entity = new RecepcionCostoAdicional();
            
            // Map basic fields
            entity.setId(input.getId());
            entity.setDescripcion(input.getDescripcion());
            entity.setMonto(input.getMonto());
            
            // Map navigation properties
            if (input.getRecepcionMercaderiaId() != null) {
                entity.setRecepcionMercaderia(recepcionMercaderiaService.findById(input.getRecepcionMercaderiaId())
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderia no encontrada con ID: " + input.getRecepcionMercaderiaId())));
            }
            
            if (input.getMonedaId() != null) {
                entity.setMoneda(monedaService.findById(input.getMonedaId())
                    .orElseThrow(() -> new GraphQLException("Moneda no encontrada con ID: " + input.getMonedaId())));
            }
            
            logger.info("=== RecepcionCostoAdicional mapping completed successfully ===");
            
            RecepcionCostoAdicional savedEntity = service.save(entity);
            
            logger.info("=== RecepcionCostoAdicional saved with ID: {} ===", savedEntity.getId());
            
            return savedEntity;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during RecepcionCostoAdicional save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al guardar costo adicional: " + ex.getMessage());
        }
    }

    public Boolean deleteRecepcionCostoAdicional(Long id) {
        logger.info("Eliminando RecepcionCostoAdicional con ID: {}", id);
        try {
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar RecepcionCostoAdicional: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar costo adicional: " + ex.getMessage());
        }
    }
} 