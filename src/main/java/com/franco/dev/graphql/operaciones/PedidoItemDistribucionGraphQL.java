package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.graphql.operaciones.input.PedidoItemDistribucionInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.PedidoItemDistribucionService;
import com.franco.dev.service.operaciones.PedidoItemService;
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
public class PedidoItemDistribucionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    private static final Logger logger = LoggerFactory.getLogger(PedidoItemDistribucionGraphQL.class);

    @Autowired
    private PedidoItemDistribucionService service;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private SucursalService sucursalService;

    // ===== QUERY OPERATIONS =====

    public Optional<PedidoItemDistribucion> pedidoItemDistribucion(Long id) {
        return service.findById(id);
    }

    public List<PedidoItemDistribucion> pedidoItemDistribucionPorPedidoItem(Long pedidoItemId) {
        logger.info("Buscando distribuciones para PedidoItem ID: {}", pedidoItemId);
        try {
            return service.findByPedidoItemId(pedidoItemId);
        } catch (Exception ex) {
            logger.error("Error al buscar distribuciones por PedidoItem: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar distribuciones: " + ex.getMessage());
        }
    }

    public List<PedidoItemDistribucion> pedidoItemDistribucionPorSucursal(Long sucursalId) {
        logger.info("Buscando distribuciones para Sucursal ID: {}", sucursalId);
        try {
            return service.findBySucursalEntregaId(sucursalId);
        } catch (Exception ex) {
            logger.error("Error al buscar distribuciones por Sucursal: {}", ex.getMessage());
            throw new GraphQLException("Error al buscar distribuciones: " + ex.getMessage());
        }
    }

    public Long countPedidoItemDistribucion() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public PedidoItemDistribucion savePedidoItemDistribucion(PedidoItemDistribucionInput input) {
        logger.info("=== Starting PedidoItemDistribucion save operation ===");
        
        try {
            PedidoItemDistribucion entity = new PedidoItemDistribucion();
            
            // Map basic fields
            entity.setId(input.getId());
            
            // IMPORTANT: Map cantidadSolicitada (input) to cantidadAsignada (entity)
            entity.setCantidadAsignada(input.getCantidadSolicitada());
            
            // Map navigation properties
            if (input.getPedidoItemId() != null) {
                entity.setPedidoItem(pedidoItemService.findById(input.getPedidoItemId())
                    .orElseThrow(() -> new GraphQLException("PedidoItem no encontrado con ID: " + input.getPedidoItemId())));
            }
            
            if (input.getSucursalEntregaId() != null) {
                entity.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId())
                    .orElseThrow(() -> new GraphQLException("Sucursal no encontrada con ID: " + input.getSucursalEntregaId())));
            }
            
            logger.info("=== PedidoItemDistribucion mapping completed successfully ===");
            
            PedidoItemDistribucion savedEntity = service.save(entity);
            
            logger.info("=== PedidoItemDistribucion saved with ID: {} ===", savedEntity.getId());
            
            return savedEntity;
            
        } catch (Exception ex) {
            logger.error("=== ERROR during PedidoItemDistribucion save ===");
            logger.error("Exception: {}", ex.getMessage());
            throw new GraphQLException("Error al guardar distribución: " + ex.getMessage());
        }
    }

    public Boolean deletePedidoItemDistribucion(Long id) {
        logger.info("Eliminando PedidoItemDistribucion con ID: {}", id);
        try {
            return service.deleteById(id);
        } catch (Exception ex) {
            logger.error("Error al eliminar PedidoItemDistribucion: {}", ex.getMessage());
            throw new GraphQLException("Error al eliminar distribución: " + ex.getMessage());
        }
    }
} 