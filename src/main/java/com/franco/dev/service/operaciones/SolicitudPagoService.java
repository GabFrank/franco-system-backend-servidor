package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoRecepcion;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;

import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Service
@AllArgsConstructor
public class SolicitudPagoService extends CrudService<SolicitudPago, SolicitudPagoRepository, Long> {
    private final SolicitudPagoRepository repository;
    
    @Autowired
    private SolicitudPagoRecepcionService solicitudPagoRecepcionService;
    
    @Autowired
    private ProcesoEtapaService procesoEtapaService;

    @Override
    public SolicitudPagoRepository getRepository() {
        return repository;
    }

    @Override
    public SolicitudPago save(SolicitudPago entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(SolicitudPagoEstado.PENDIENTE);
            // TODO: Set a default tipo if not provided - needs refactored entity
            // if (entity.getTipo() == null) {
            //     entity.setTipo(TipoSolicitudPago.GASTO);
            // }
        }
        return super.save(entity);
    }
    
    /**
     * Find a SolicitudPago by tipo and referenciaId
     * @param tipo The type of solicitud pago (e.g., COMPRA)
     * @param referenciaId The ID of the referenced entity
     * @return The SolicitudPago or null if not found
     */
    // public SolicitudPago findByTipoAndReferenciaId(TipoSolicitudPago tipo, Long referenciaId) {
    //     return repository.findByTipoAndReferenciaId(tipo, referenciaId);
    // }
    
    /**
     * Find all SolicitudPago by pago ID
     * @param pagoId The ID of the pago
     * @return List of SolicitudPago associated with the pago
     */
    public List<SolicitudPago> findByPagoId(Long pagoId) {
        return repository.findByPagoId(pagoId);
    }
    
    /**
     * Find all SolicitudPago with filters using JPA Specifications
     * @param solicitudPagoId The ID of the solicitud pago (can be null)
     * @param proveedorId The ID of the proveedor (can be null)
     * @param estado The state of solicitud pago (can be null)
     * @param fechaInicioStr Start date string in ISO format (can be null)
     * @param fechaFinStr End date string in ISO format (can be null)
     * @param page Page number
     * @param size Page size
     * @return Page of SolicitudPago matching the criteria
     */
    public Page<SolicitudPago> findAllWithFilters(
            Long solicitudPagoId,
            Long proveedorId,
            SolicitudPagoEstado estado,
            String fechaInicioStr,
            String fechaFinStr,
            int page,
            int size) {
        
        // Parse date strings if provided
        final LocalDateTime fechaInicio;
        final LocalDateTime fechaFin;
        
        if (fechaInicioStr != null && !fechaInicioStr.isEmpty()) {
            fechaInicio = LocalDateTime.parse(fechaInicioStr, DateTimeFormatter.ISO_DATE_TIME);
        } else {
            fechaInicio = null;
        }
        
        if (fechaFinStr != null && !fechaFinStr.isEmpty()) {
            fechaFin = LocalDateTime.parse(fechaFinStr, DateTimeFormatter.ISO_DATE_TIME);
        } else {
            fechaFin = null;
        }
        
        // Create specification for dynamic filtering
        Specification<SolicitudPago> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Filter by ID if provided
            if (solicitudPagoId != null) {
                predicates.add(cb.equal(root.get("id"), solicitudPagoId));
            }
            
            // Filter by proveedorId if provided
            if (proveedorId != null) {
                predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
            }
            
            // Filter by estado if provided
            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }
            
            // Filter by date range if provided
            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), fechaInicio));
            }
            
            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("creadoEn"), fechaFin));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAll(spec, pageable);
    }

    /**
     * Crea una solicitud de pago para múltiples recepciones de mercadería - NUEVA FUNCIONALIDAD
     */
    @Transactional
    public SolicitudPago crearSolicitudPagoMultipleRecepciones(Proveedor proveedor,
                                                              String descripcion,
                                                              List<RecepcionMercaderia> recepciones,
                                                              Usuario usuario) {
        // Crear la solicitud de pago
        SolicitudPago solicitud = new SolicitudPago();
        solicitud.setProveedor(proveedor);
        // TODO: solicitud.setDescripcion(descripcion); - needs refactored entity
        solicitud.setUsuario(usuario);
        solicitud.setEstado(SolicitudPagoEstado.PENDIENTE);
        
        // Calcular monto total de todas las recepciones
        Double montoTotal = recepciones.stream()
            .mapToDouble(recepcion -> calcularMontoRecepcion(recepcion))
            .sum();
        // TODO: solicitud.setMonto(montoTotal); - needs refactored entity
        
        SolicitudPago solicitudGuardada = save(solicitud);
        
        // Asociar todas las recepciones a la solicitud
        for (RecepcionMercaderia recepcion : recepciones) {
            Double montoRecepcion = calcularMontoRecepcion(recepcion);
            solicitudPagoRecepcionService.asociarRecepcionASolicitud(
                solicitudGuardada, recepcion, montoRecepcion);
        }
        
        return solicitudGuardada;
    }

    /**
     * Calcula el monto total de una recepción de mercadería
     */
    private Double calcularMontoRecepcion(RecepcionMercaderia recepcion) {
        // TODO: Implementar cálculo real del monto de la recepción
        // Esto debería sumar todos los ítems recibidos por sus precios
        // más los costos adicionales
        return 0.0; // Placeholder
    }

    /**
     * Obtiene todas las recepciones asociadas a una solicitud de pago
     */
    public List<RecepcionMercaderia> getRecepcionesAsociadas(Long solicitudPagoId) {
        List<SolicitudPagoRecepcion> asociaciones = solicitudPagoRecepcionService
            .findBySolicitudPagoId(solicitudPagoId);
        
        return asociaciones.stream()
            .map(SolicitudPagoRecepcion::getRecepcionMercaderia)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Procesa el pago de una solicitud - NUEVA FUNCIONALIDAD
     * Actualiza las etapas del proceso para todos los pedidos relacionados
     */
    @Transactional
    public SolicitudPago procesarPago(Long solicitudPagoId) {
        SolicitudPago solicitud = findById(solicitudPagoId).orElseThrow(
            () -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudPagoId)
        );
        
        if (solicitud.getEstado() == SolicitudPagoEstado.CONCLUIDO) {
            throw new IllegalStateException("La solicitud de pago ya está pagada");
        }
        
        // Cambiar estado de la solicitud
        solicitud.setEstado(SolicitudPagoEstado.CONCLUIDO);
        SolicitudPago solicitudPagada = save(solicitud);
        
        // Actualizar etapas del proceso para todos los pedidos relacionados
        actualizarEtapasProcesoPostPago(solicitud);
        
        return solicitudPagada;
    }

    /**
     * Actualiza las etapas del proceso después del pago
     */
    private void actualizarEtapasProcesoPostPago(SolicitudPago solicitud) {
        List<RecepcionMercaderia> recepciones = getRecepcionesAsociadas(solicitud.getId());
        
        for (RecepcionMercaderia recepcion : recepciones) {
            // TODO: Obtener pedidos relacionados a la recepción
            // y finalizar la etapa de pago para cada uno
            // procesoEtapaService.finalizarEtapa(pedidoId, ProcesoEtapaTipo.PAGO);
        }
    }
}

