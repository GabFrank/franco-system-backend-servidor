package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcionItemDistribucion;
import com.franco.dev.graphql.operaciones.input.NotaRecepcionItemDistribucionInput;
import com.franco.dev.service.operaciones.NotaRecepcionItemDistribucionService;
import com.franco.dev.service.operaciones.NotaRecepcionItemService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class NotaRecepcionItemDistribucionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaRecepcionItemDistribucionService service;

    @Autowired
    private NotaRecepcionItemService notaRecepcionItemService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModelMapper modelMapper;

    // ===============================================
    // QUERIES
    // ===============================================

    public NotaRecepcionItemDistribucion notaRecepcionItemDistribucion(Long id) {
        return service.findById(id).orElse(null);
    }

    public List<NotaRecepcionItemDistribucion> notaRecepcionItemDistribucionesByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return service.findByNotaRecepcionItemId(notaRecepcionItemId);
    }

    public List<NotaRecepcionItemDistribucion> notaRecepcionItemDistribucionesBySucursalEntregaId(Long sucursalId) {
        return service.findBySucursalEntregaId(sucursalId);
    }

    public List<NotaRecepcionItemDistribucion> notaRecepcionItemDistribucionesByNotaRecepcionId(Long notaRecepcionId) {
        return service.findByNotaRecepcionId(notaRecepcionId);
    }

    public Double totalDistributedQuantityByNotaRecepcionItemId(Long notaRecepcionItemId) {
        return service.getTotalDistributedQuantityByNotaRecepcionItemId(notaRecepcionItemId);
    }

    public Double distributedQuantityByNotaRecepcionItemIdAndSucursalId(Long notaRecepcionItemId, Long sucursalId) {
        return service.getDistributedQuantityByNotaRecepcionItemIdAndSucursalId(notaRecepcionItemId, sucursalId);
    }

    // ===============================================
    // MUTATIONS
    // ===============================================

    public NotaRecepcionItemDistribucion saveNotaRecepcionItemDistribucion(NotaRecepcionItemDistribucionInput input) {
        NotaRecepcionItemDistribucion entity = modelMapper.map(input, NotaRecepcionItemDistribucion.class);
        
        // Setear las relaciones
        if (input.getNotaRecepcionItemId() != null) {
            entity.setNotaRecepcionItem(notaRecepcionItemService.findById(input.getNotaRecepcionItemId()).orElse(null));
        }
        if (input.getSucursalEntregaId() != null) {
            entity.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId()).orElse(null));
        }
        if (input.getUsuarioId() != null) {
            entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        
        return service.save(entity);
    }

    public List<NotaRecepcionItemDistribucion> saveNotaRecepcionItemDistribuciones(List<NotaRecepcionItemDistribucionInput> inputs) {
        List<NotaRecepcionItemDistribucion> entities = inputs.stream()
                .map(input -> {
                    NotaRecepcionItemDistribucion entity = modelMapper.map(input, NotaRecepcionItemDistribucion.class);
                    
                    // Setear las relaciones
                    if (input.getNotaRecepcionItemId() != null) {
                        entity.setNotaRecepcionItem(notaRecepcionItemService.findById(input.getNotaRecepcionItemId()).orElse(null));
                    }
                    if (input.getSucursalEntregaId() != null) {
                        entity.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId()).orElse(null));
                    }
                    if (input.getUsuarioId() != null) {
                        entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
                    }
                    
                    return entity;
                })
                .collect(Collectors.toList());
        
        return service.saveDistribuciones(entities);
    }

    public List<NotaRecepcionItemDistribucion> replaceNotaRecepcionItemDistribuciones(Long notaRecepcionItemId, List<NotaRecepcionItemDistribucionInput> inputs) {
        List<NotaRecepcionItemDistribucion> entities = inputs.stream()
                .map(input -> {
                    NotaRecepcionItemDistribucion entity = modelMapper.map(input, NotaRecepcionItemDistribucion.class);
                    
                    // Setear las relaciones
                    if (input.getNotaRecepcionItemId() != null) {
                        entity.setNotaRecepcionItem(notaRecepcionItemService.findById(input.getNotaRecepcionItemId()).orElse(null));
                    }
                    if (input.getSucursalEntregaId() != null) {
                        entity.setSucursalEntrega(sucursalService.findById(input.getSucursalEntregaId()).orElse(null));
                    }
                    if (input.getUsuarioId() != null) {
                        entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
                    }
                    
                    return entity;
                })
                .collect(Collectors.toList());
        
        return service.replaceDistribuciones(notaRecepcionItemId, entities);
    }

    public Boolean deleteNotaRecepcionItemDistribucion(Long id) {
        return service.deleteById(id);
    }

    public Boolean deleteNotaRecepcionItemDistribucionesByNotaRecepcionItemId(Long notaRecepcionItemId) {
        try {
            service.deleteByNotaRecepcionItemId(notaRecepcionItemId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 