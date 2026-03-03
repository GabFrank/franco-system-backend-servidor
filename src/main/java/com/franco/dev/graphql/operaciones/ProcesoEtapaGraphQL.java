package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.ProcesoEtapa;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.graphql.operaciones.input.ProcesoEtapaInput;
import com.franco.dev.service.operaciones.PedidoService;
import com.franco.dev.service.operaciones.ProcesoEtapaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class ProcesoEtapaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProcesoEtapaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PedidoService pedidoService;

    /**
     * Obtiene todas las etapas de un pedido
     */
    public List<ProcesoEtapa> procesoEtapasByPedidoId(Long pedidoId) {
        if (pedidoId == null) {
            throw new GraphQLException("ID del pedido es requerido");
        }
        return service.getEtapasByPedidoId(pedidoId);
    }

    /**
     * Obtiene una etapa específica por pedido y tipo
     */
    public ProcesoEtapa procesoEtapaByPedidoAndTipo(Long pedidoId, ProcesoEtapaTipo tipo) {
        if (pedidoId == null || tipo == null) {
            throw new GraphQLException("ID del pedido y tipo de etapa son requeridos");
        }
        Optional<ProcesoEtapa> etapa = service.getEtapaByPedidoAndTipo(pedidoId, tipo);
        return etapa.orElse(null);
    }

    /**
     * Obtiene la etapa actual de un pedido
     */
    public ProcesoEtapaTipo etapaActualPorPedido(Long pedidoId) {
        if (pedidoId == null) {
            throw new GraphQLException("ID del pedido es requerido");
        }
        return service.getEtapaTipoActual(pedidoId);
    }

    /**
     * Verifica si una etapa está completada
     */
    public Boolean isEtapaCompletada(Long pedidoId, ProcesoEtapaTipo tipo) {
        if (pedidoId == null || tipo == null) {
            throw new GraphQLException("ID del pedido y tipo de etapa son requeridos");
        }
        return service.isEtapaCompletada(pedidoId, tipo);
    }

    /**
     * Guarda o actualiza una etapa del proceso
     */
    public ProcesoEtapa saveProcesoEtapa(ProcesoEtapaInput input) {
        ModelMapper mapper = new ModelMapper();
        ProcesoEtapa entity = mapper.map(input, ProcesoEtapa.class);
        
        if (input.getUsuarioInicioId() != null) {
            entity.setUsuarioInicio(usuarioService.findById(input.getUsuarioInicioId()).orElse(null));
        }
        
        if (input.getFechaInicio() != null) {
            entity.setFechaInicio(stringToDate(input.getFechaInicio()));
        }
        
        if (input.getFechaFin() != null) {
            entity.setFechaFin(stringToDate(input.getFechaFin()));
        }
        
        if (input.getCreadoEn() != null) {
            entity.setCreadoEn(stringToDate(input.getCreadoEn()));
        }
        
        return service.save(entity);
    }

    /**
     * Inicia una etapa específica para un pedido
     */
    @Transactional
    public ProcesoEtapa iniciarEtapa(Long pedidoId, ProcesoEtapaTipo tipo, Long usuarioId) {
        if (pedidoId == null || tipo == null || usuarioId == null) {
            throw new GraphQLException("ID del pedido, tipo de etapa y usuario son requeridos");
        }
        
        try {
            com.franco.dev.domain.operaciones.Pedido pedido = new com.franco.dev.domain.operaciones.Pedido();
            pedido.setId(pedidoId);
            
            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + usuarioId));
            
            return service.iniciarEtapa(pedido, tipo, usuario);
        } catch (Exception e) {
            throw new GraphQLException("Error al iniciar etapa: " + e.getMessage());
        }
    }

    /**
     * Finaliza una etapa específica para un pedido
     */
    @Transactional
    public ProcesoEtapa finalizarEtapa(Long pedidoId, ProcesoEtapaTipo tipo) {
        if (pedidoId == null || tipo == null) {
            throw new GraphQLException("ID del pedido y tipo de etapa son requeridos");
        }
        
        try {
            return service.finalizarEtapa(pedidoId, tipo);
        } catch (Exception e) {
            throw new GraphQLException("Error al finalizar etapa: " + e.getMessage());
        }
    }

    /**
     * Omite una etapa específica para un pedido
     */
    @Transactional
    public ProcesoEtapa omitirEtapa(Long pedidoId, ProcesoEtapaTipo tipo) {
        if (pedidoId == null || tipo == null) {
            throw new GraphQLException("ID del pedido y tipo de etapa son requeridos");
        }
        
        try {
            return service.omitirEtapa(pedidoId, tipo);
        } catch (Exception e) {
            throw new GraphQLException("Error al omitir etapa: " + e.getMessage());
        }
    }

    /**
     * Revierte la etapa CREACION de COMPLETADA a EN_PROCESO
     * Solo se permite si RECEPCION_NOTA está en estado PENDIENTE (no ha empezado)
     * Retorna el pedido actualizado
     */
    @Transactional
    public Pedido revertirEtapaCreacion(Long pedidoId) {
        if (pedidoId == null) {
            throw new GraphQLException("ID del pedido es requerido");
        }
        
        try {
            // Revertir la etapa CREACION
            service.revertirEtapaCreacion(pedidoId);
            
            // Retornar el pedido actualizado
            return pedidoService.findById(pedidoId)
                .orElseThrow(() -> new GraphQLException("Pedido no encontrado: " + pedidoId));
        } catch (IllegalStateException e) {
            throw new GraphQLException(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error revirtiendo etapa CREACION para pedido " + pedidoId + ": " + e.getMessage());
            e.printStackTrace();
            throw new GraphQLException("Error al revertir etapa CREACION: " + e.getMessage());
        }
    }
} 