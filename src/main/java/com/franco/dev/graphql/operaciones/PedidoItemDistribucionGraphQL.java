package com.franco.dev.graphql.operaciones;

import com.franco.dev.service.operaciones.PedidoItemDistribucionService;
import com.franco.dev.service.operaciones.PedidoItemService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.franco.dev.graphql.operaciones.input.PedidoItemDistribucionInput;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.domain.operaciones.PedidoItemDistribucion;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Component
public class PedidoItemDistribucionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PedidoItemDistribucionService service;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    // QUERIES

    /**
     * Busca una distribución por ID
     */
    public PedidoItemDistribucion pedidoItemDistribucion(Long id) {
        return service.findById(id)
                .orElseThrow(() -> new GraphQLException("PedidoItemDistribucion no encontrado con ID: " + id));
    }

    /**
     * Busca distribuciones por ID de PedidoItem
     */
    public List<PedidoItemDistribucion> pedidoItemDistribucionPorPedidoItem(Long pedidoItemId) {
        return service.findByPedidoItemId(pedidoItemId);
    }

    /**
     * Busca distribuciones por ID de Sucursal de entrega
     */
    public List<PedidoItemDistribucion> pedidoItemDistribucionPorSucursal(Long sucursalId) {
        return service.findBySucursalEntregaId(sucursalId);
    }

    /**
     * Busca distribuciones por ID de Sucursal de influencia
     */
    public List<PedidoItemDistribucion> pedidoItemDistribucionPorSucursalInfluencia(Long sucursalId) {
        return service.findBySucursalInfluenciaId(sucursalId);
    }

    /**
     * Cuenta el total de distribuciones
     */
    public Integer countPedidoItemDistribucion() {
        return Math.toIntExact(service.count());
    }

    // MUTATIONS

    /**
     * Guarda una distribución
     */
    @Transactional
    public PedidoItemDistribucion savePedidoItemDistribucion(PedidoItemDistribucionInput input) {
        PedidoItemDistribucion distribucion;

        // Si tiene ID, buscar la existente para actualizar
        if (input.getId() != null) {
            distribucion = service.findById(input.getId())
                    .orElseThrow(() -> new GraphQLException(
                            "PedidoItemDistribucion no encontrado con ID: " + input.getId()));
        } else {
            distribucion = new PedidoItemDistribucion();
        }

        // Establecer PedidoItem
        if (input.getPedidoItemId() != null) {
            PedidoItem pedidoItem = pedidoItemService.findById(input.getPedidoItemId())
                    .orElseThrow(
                            () -> new GraphQLException("PedidoItem no encontrado con ID: " + input.getPedidoItemId()));
            distribucion.setPedidoItem(pedidoItem);
        }

        // Establecer Sucursal de influencia
        if (input.getSucursalInfluenciaId() != null) {
            distribucion.setSucursalInfluencia(sucursalService.findById(input.getSucursalInfluenciaId())
                    .orElseThrow(() -> new GraphQLException(
                            "Sucursal de influencia no encontrada con ID: " + input.getSucursalInfluenciaId())));
        }

        // Establecer Sucursal de entrega
        if (input.getSucursalEntregaId() != null) {
            distribucion.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId())
                    .orElseThrow(() -> new GraphQLException(
                            "Sucursal de entrega no encontrada con ID: " + input.getSucursalEntregaId())));
        }

        // Establecer cantidad
        if (input.getCantidadAsignada() != null) {
            distribucion.setCantidadAsignada(input.getCantidadAsignada());
        }

        return service.save(distribucion);
    }

    /**
     * Elimina una distribución por ID
     */
    @Transactional
    public Boolean deletePedidoItemDistribucion(Long id) {
        try {
            Optional<PedidoItemDistribucion> distribucion = service.findById(id);
            if (distribucion.isPresent()) {
                service.deleteById(id);
                return true;
            } else {
                throw new GraphQLException("PedidoItemDistribucion no encontrado con ID: " + id);
            }
        } catch (Exception e) {
            throw new GraphQLException(
                    "Error al eliminar PedidoItemDistribucion con ID: " + id + ". " + e.getMessage());
        }
    }

    /**
     * Guarda múltiples distribuciones
     */
    @Transactional
    public List<PedidoItemDistribucion> savePedidoItemDistribuciones(List<PedidoItemDistribucionInput> inputs) {
        List<PedidoItemDistribucion> distribuciones = new ArrayList<>();

        for (PedidoItemDistribucionInput input : inputs) {
            PedidoItemDistribucion distribucion = savePedidoItemDistribucion(input);
            distribuciones.add(distribucion);
        }

        return distribuciones;
    }

    /**
     * Reemplaza todas las distribuciones de un ítem de pedido
     */
    @Transactional
    public List<PedidoItemDistribucion> replacePedidoItemDistribuciones(Long pedidoItemId,
            List<PedidoItemDistribucionInput> inputs) {
        // Eliminar distribuciones existentes
        deletePedidoItemDistribucionesByPedidoItemId(pedidoItemId);

        // Guardar nuevas distribuciones
        return savePedidoItemDistribuciones(inputs);
    }

    /**
     * Elimina todas las distribuciones de un ítem de pedido
     */
    @Transactional
    public Boolean deletePedidoItemDistribucionesByPedidoItemId(Long pedidoItemId) {
        try {
            // Use the repository method that handles the deletion in a single query
            service.deleteByPedidoItemId(pedidoItemId);
            return true;
        } catch (Exception e) {
            throw new GraphQLException(
                    "Error al eliminar distribuciones del PedidoItem con ID: " + pedidoItemId + ". " + e.getMessage());
        }
    }

    /**
     * Elimina múltiples distribuciones por IDs
     */
    @Transactional
    public Boolean deletePedidoItemDistribucionesByIds(List<Long> ids) {
        try {
            int deletedCount = service.deleteByIds(ids);
            return deletedCount > 0 || ids.isEmpty();
        } catch (Exception e) {
            throw new GraphQLException("Error al eliminar distribuciones. " + e.getMessage());
        }
    }

    // MÉTODO EXISTENTE CORREGIDO

    @Transactional
    public List<PedidoItemDistribucion> savePedidoItemDistribuciones(Long pedidoItemId,
            List<PedidoItemDistribucionInput> distribucionesInput) {
        // Step 1: Find the PedidoItem
        PedidoItem pedidoItem = pedidoItemService.findById(pedidoItemId)
                .orElseThrow(() -> new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId));

        // Step 2: Delete all existing distributions for this PedidoItem
        service.deleteByPedidoItemId(pedidoItemId);

        List<PedidoItemDistribucion> distribuciones = new ArrayList<>();

        // Step 3: Iterate and save the new distributions
        for (PedidoItemDistribucionInput input : distribucionesInput) {
            PedidoItemDistribucion nuevaDistribucion = new PedidoItemDistribucion();
            nuevaDistribucion.setPedidoItem(pedidoItem);
            nuevaDistribucion.setCantidadAsignada(input.getCantidadAsignada());

            // Find and set SucursalInfluencia
            nuevaDistribucion.setSucursalInfluencia(sucursalService.findById(input.getSucursalInfluenciaId())
                    .orElseThrow(() -> new GraphQLException(
                            "Sucursal de influencia no encontrada con ID: " + input.getSucursalInfluenciaId())));

            // Find and set SucursalEntrega
            nuevaDistribucion.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId())
                    .orElseThrow(() -> new GraphQLException(
                            "Sucursal de entrega no encontrada con ID: " + input.getSucursalEntregaId())));

            distribuciones.add(service.save(nuevaDistribucion));
        }

        // Step 4: Return the updated PedidoItem
        return distribuciones;
    }

    /**
     * Merge inteligente de distribuciones: actualiza existentes, crea nuevas, elimina las que ya no están
     * Mantiene los IDs de las distribuciones existentes cuando es posible
     */
    @Transactional
    public List<PedidoItemDistribucion> mergePedidoItemDistribuciones(Long pedidoItemId,
            List<PedidoItemDistribucionInput> inputs) {
        // Step 1: Find the PedidoItem
        PedidoItem pedidoItem = pedidoItemService.findById(pedidoItemId)
                .orElseThrow(() -> new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId));

        // Step 2: Obtener distribuciones existentes
        List<PedidoItemDistribucion> distribucionesExistentes = service.findByPedidoItemId(pedidoItemId);
        
        // Step 3: Crear mapa de distribuciones existentes por combinación (sucursal_influencia_id + sucursal_entrega_id)
        // Clave: "sucursalInfluenciaId_sucursalEntregaId"
        java.util.Map<String, PedidoItemDistribucion> mapaExistentes = new java.util.HashMap<>();
        for (PedidoItemDistribucion dist : distribucionesExistentes) {
            String key = dist.getSucursalInfluencia().getId() + "_" + dist.getSucursalEntrega().getId();
            mapaExistentes.put(key, dist);
        }

        // Step 4: Crear mapa de distribuciones nuevas por combinación
        java.util.Map<String, PedidoItemDistribucionInput> mapaNuevas = new java.util.HashMap<>();
        for (PedidoItemDistribucionInput input : inputs) {
            if (input.getCantidadAsignada() != null && input.getCantidadAsignada() > 0) {
                String key = input.getSucursalInfluenciaId() + "_" + input.getSucursalEntregaId();
                mapaNuevas.put(key, input);
            }
        }

        // Step 5: Procesar distribuciones: actualizar existentes o crear nuevas
        List<PedidoItemDistribucion> distribucionesResultado = new ArrayList<>();
        for (PedidoItemDistribucionInput input : inputs) {
            if (input.getCantidadAsignada() != null && input.getCantidadAsignada() > 0) {
                String key = input.getSucursalInfluenciaId() + "_" + input.getSucursalEntregaId();
                PedidoItemDistribucion distribucionExistente = mapaExistentes.get(key);
                
                PedidoItemDistribucion distribucion;
                if (distribucionExistente != null) {
                    // ACTUALIZAR: Mantener el ID existente
                    distribucion = distribucionExistente;
                } else {
                    // CREAR: Nueva distribución
                    distribucion = new PedidoItemDistribucion();
                    distribucion.setPedidoItem(pedidoItem);
                }
                
                // Actualizar campos
                distribucion.setCantidadAsignada(input.getCantidadAsignada());
                
                // Establecer SucursalInfluencia
                distribucion.setSucursalInfluencia(sucursalService.findById(input.getSucursalInfluenciaId())
                        .orElseThrow(() -> new GraphQLException(
                                "Sucursal de influencia no encontrada con ID: " + input.getSucursalInfluenciaId())));
                
                // Establecer SucursalEntrega
                distribucion.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId())
                        .orElseThrow(() -> new GraphQLException(
                                "Sucursal de entrega no encontrada con ID: " + input.getSucursalEntregaId())));
                
                distribucionesResultado.add(service.save(distribucion));
            }
        }

        // Step 6: Eliminar distribuciones que ya no están en los inputs nuevos
        List<Long> idsAEliminar = new ArrayList<>();
        for (PedidoItemDistribucion dist : distribucionesExistentes) {
            String key = dist.getSucursalInfluencia().getId() + "_" + dist.getSucursalEntrega().getId();
            if (!mapaNuevas.containsKey(key)) {
                idsAEliminar.add(dist.getId());
            }
        }
        
        if (!idsAEliminar.isEmpty()) {
            service.deleteByIds(idsAEliminar);
        }

        return distribucionesResultado;
    }

    public Boolean isDistribucionConcluida(Long pedidoItemId) {
        PedidoItem pedidoItem = pedidoItemService.findById(pedidoItemId)
                .orElseThrow(() -> new GraphQLException("PedidoItem no encontrado con ID: " + pedidoItemId));

        return service.isDistribucionConcluida(pedidoItemId, pedidoItem.getCantidadSolicitada());
    }
}