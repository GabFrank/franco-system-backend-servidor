package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoInput;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoMultipleRecepcionesInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.PagoService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

import java.util.List;

@Component
public class SolicitudPagoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private SolicitudPagoService service;

    @Autowired
    private UsuarioService usuarioService;
    
    @Autowired
    private ProveedorService proveedorService;
    
    @Autowired
    private MonedaService monedaService;
    
    @Autowired
    private FormaPagoService formaPagoService;
    
    @Autowired
    private PagoService pagoService;
    
    @Autowired
    private RecepcionMercaderiaService recepcionMercaderiaService;

    // ===== QUERY OPERATIONS =====

    public SolicitudPago solicitudPago(Long id) {
        return service.findById(id).orElse(null);
    }

    public List<SolicitudPago> solicitudPagoPorUsuarioId(Long id) {
        return service.getRepository().findByUsuarioId(id);
    }

    /**
     * Query to filter SolicitudPago by multiple criteria
     */
    public Page<SolicitudPago> solicitudPagoConFiltros(
            Long solicitudPagoId,
            Long proveedorId,
            SolicitudPagoEstado estado,
            String fechaInicio,
            String fechaFin,
            Integer page,
            Integer size) {
        
        return service.findAllWithFilters(
                solicitudPagoId,
                proveedorId,
                estado,
                fechaInicio,
                fechaFin,
                page,
                size
        );
    }

    public Long countSolicitudPago() {
        return service.count();
    }

    // ===== MUTATION OPERATIONS =====

    public SolicitudPago saveSolicitudPago(SolicitudPagoInput input) {
        try {
            SolicitudPago entity = new SolicitudPago();
            
            // Map basic fields
            entity.setId(input.getId());
            entity.setMontoTotal(input.getMontoTotal());
            entity.setEstado(input.getEstado());
            
            // Map navigation properties
            if (input.getProveedorId() != null) {
                entity.setProveedor(proveedorService.findById(input.getProveedorId())
                    .orElseThrow(() -> new GraphQLException("Proveedor no encontrado con ID: " + input.getProveedorId())));
            }
            
            if (input.getMonedaId() != null) {
                entity.setMoneda(monedaService.findById(input.getMonedaId())
                    .orElseThrow(() -> new GraphQLException("Moneda no encontrada con ID: " + input.getMonedaId())));
            }
            
            if (input.getFormaPagoId() != null) {
                entity.setFormaPago(formaPagoService.findById(input.getFormaPagoId())
                    .orElseThrow(() -> new GraphQLException("Forma de pago no encontrada con ID: " + input.getFormaPagoId())));
            }
            
            if (input.getUsuarioId() != null) {
                entity.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
            }
            
            if (input.getPagoId() != null) {
                entity.setPago(pagoService.findById(input.getPagoId()).orElse(null));
            }
            
            if (input.getCreadoEn() != null) {
                entity.setCreadoEn(stringToDate(input.getCreadoEn()));
            }
            
            return service.save(entity);
            
        } catch (Exception e) {
            throw new GraphQLException("Error al guardar solicitud de pago: " + e.getMessage());
        }
    }

    public Boolean deleteSolicitudPago(Long id) {
        return service.deleteById(id);
    }

    // ===== NUEVAS FUNCIONALIDADES - REFACTOR =====

    /**
     * Crea una solicitud de pago para múltiples recepciones de mercadería
     */
    @Transactional
    public SolicitudPago crearSolicitudPagoMultipleRecepciones(SolicitudPagoMultipleRecepcionesInput input) {
        try {
            // Validar entrada
            if (input.getProveedorId() == null || input.getRecepcionMercaderiaIds() == null || 
                input.getRecepcionMercaderiaIds().isEmpty() || input.getUsuarioId() == null) {
                throw new GraphQLException("Proveedor, recepciones y usuario son requeridos");
            }

            // Obtener entidades
            com.franco.dev.domain.personas.Proveedor proveedor = proveedorService.findById(input.getProveedorId())
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + input.getProveedorId()));

            com.franco.dev.domain.personas.Usuario usuario = usuarioService.findById(input.getUsuarioId())
                .orElseThrow(() -> new GraphQLException("Usuario no encontrado: " + input.getUsuarioId()));

            // Obtener recepciones
            java.util.List<RecepcionMercaderia> recepciones = new java.util.ArrayList<>();
            for (Long recepcionId : input.getRecepcionMercaderiaIds()) {
                RecepcionMercaderia recepcion = recepcionMercaderiaService.findById(recepcionId)
                    .orElseThrow(() -> new GraphQLException("Recepción no encontrada: " + recepcionId));
                recepciones.add(recepcion);
            }

            // Crear solicitud usando el service
            return service.crearSolicitudPagoMultipleRecepciones(
                proveedor, input.getDescripcion(), recepciones, usuario);

        } catch (Exception e) {
            throw new GraphQLException("Error al crear solicitud de pago: " + e.getMessage());
        }
    }

    /**
     * Procesa el pago de una solicitud
     * Actualiza las etapas del proceso para todos los pedidos relacionados
     */
    @Transactional
    public SolicitudPago procesarPagoSolicitud(Long solicitudPagoId) {
        if (solicitudPagoId == null) {
            throw new GraphQLException("ID de solicitud de pago es requerido");
        }

        try {
            return service.procesarPago(solicitudPagoId);
        } catch (Exception e) {
            throw new GraphQLException("Error al procesar pago: " + e.getMessage());
        }
    }
}

