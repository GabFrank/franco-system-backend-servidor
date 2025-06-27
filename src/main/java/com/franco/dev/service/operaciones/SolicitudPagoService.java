package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class SolicitudPagoService extends CrudService<SolicitudPago, SolicitudPagoRepository, Long> {
    private final SolicitudPagoRepository repository;

    @Override
    public SolicitudPagoRepository getRepository() {
        return repository;
    }

    @Override
    public SolicitudPago save(SolicitudPago entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
            entity.setEstado(SolicitudPagoEstado.PENDIENTE);
            // Set a default tipo if not provided
            if (entity.getTipo() == null) {
                entity.setTipo(TipoSolicitudPago.GASTO);
            }
        }
        return super.save(entity);
    }
    
    /**
     * Find a SolicitudPago by tipo and referenciaId
     * @param tipo The type of solicitud pago (e.g., COMPRA)
     * @param referenciaId The ID of the referenced entity
     * @return The SolicitudPago or null if not found
     */
    public SolicitudPago findByTipoAndReferenciaId(TipoSolicitudPago tipo, Long referenciaId) {
        return repository.findByTipoAndReferenciaId(tipo, referenciaId);
    }
    
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
     * @param referenciaId The ID of the referenced entity (can be null)
     * @param tipo The type of solicitud pago (can be null)
     * @param estado The state of solicitud pago (can be null)
     * @param fechaInicioStr Start date string in ISO format (can be null)
     * @param fechaFinStr End date string in ISO format (can be null)
     * @param page Page number
     * @param size Page size
     * @return Page of SolicitudPago matching the criteria
     */
    public Page<SolicitudPago> findAllWithFilters(
            Long solicitudPagoId,
            Long referenciaId,
            TipoSolicitudPago tipo,
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
            
            // Filter by referenciaId if provided
            if (referenciaId != null) {
                predicates.add(cb.equal(root.get("referenciaId"), referenciaId));
            }
            
            // Filter by tipo if provided
            if (tipo != null) {
                predicates.add(cb.equal(root.get("tipo"), tipo));
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
}

