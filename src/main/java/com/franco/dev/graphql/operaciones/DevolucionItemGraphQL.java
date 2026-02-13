package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.graphql.operaciones.input.DevolucionItemInput;
import com.franco.dev.service.operaciones.DevolucionItemService;
import com.franco.dev.service.operaciones.DevolucionService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.productos.ProductoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DevolucionItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(DevolucionItemGraphQL.class);

    @Autowired
    private DevolucionItemService service;

    @Autowired
    private DevolucionService devolucionService;

    @Autowired
    private ProductoService productoService;

    // Usar @Lazy para evitar problemas de dependencia circular
    @Autowired
    @Lazy
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    // ===== QUERY OPERATIONS =====

    public Optional<DevolucionItem> devolucionItem(Long id) {
        return service.findById(id);
    }

    public List<DevolucionItem> devolucionItemsPorDevolucion(Long devolucionId) {
        logger.info("Buscando items de devolución para Devolucion ID: {}", devolucionId);
        try {
            return service.findByDevolucionId(devolucionId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por devolución: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public List<DevolucionItem> devolucionItemsPorProducto(Long productoId) {
        logger.info("Buscando items de devolución para Producto ID: {}", productoId);
        try {
            return service.findByProductoId(productoId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por producto: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public List<DevolucionItem> devolucionItemsPorRecepcionItem(Long recepcionItemId) {
        logger.info("Buscando items de devolución para RecepcionItem ID: {}", recepcionItemId);
        try {
            return service.findByRecepcionMercaderiaItemId(recepcionItemId);
        } catch (Exception ex) {
            logger.error("Error al buscar items por recepción item: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar items de devolución: " + ex.getMessage());
        }
    }

    public Long countDevolucionItem() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public DevolucionItem saveDevolucionItem(DevolucionItemInput input) {
        logger.info("=== Starting DevolucionItem save operation ===");
        
        try {
            DevolucionItem entity = new DevolucionItem();
            
            // Map basic fields
            entity.setId(input.getId());
            entity.setCantidad(input.getCantidad());
            entity.setLote(input.getLote());
            // Note: motivo field doesn't exist in entity but exists in input/schema
            
            // Map navigation properties
            if (input.getDevolucionId() != null) {
                entity.setDevolucion(devolucionService.findById(input.getDevolucionId())
                    .orElseThrow(() -> new GraphQLException("Devolucion no encontrada con ID: " + input.getDevolucionId())));
            }
            
            if (input.getProductoId() != null) {
                entity.setProducto(productoService.findById(input.getProductoId())
                    .orElseThrow(() -> new GraphQLException("Producto no encontrado con ID: " + input.getProductoId())));
            }
            
            if (input.getRecepcionMercaderiaItemId() != null) {
                entity.setRecepcionMercaderiaItem(recepcionMercaderiaItemService.findById(input.getRecepcionMercaderiaItemId())
                    .orElseThrow(() -> new GraphQLException("RecepcionMercaderiaItem no encontrado con ID: " + input.getRecepcionMercaderiaItemId())));
            }
            
            logger.info("=== DevolucionItem mapping completed successfully ===");
            
            DevolucionItem savedEntity = service.save(entity);
            
            logger.info("=== DevolucionItem saved with ID: {} ===", savedEntity.getId());
            
            return savedEntity;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during DevolucionItem save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al guardar item de devolución: " + ex.getMessage());
        }
    }

    public Boolean deleteDevolucionItem(Long id) {
        logger.info("Eliminando DevolucionItem con ID: {}", id);
        try {
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar DevolucionItem: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar item de devolución: " + ex.getMessage());
        }
    }
} 