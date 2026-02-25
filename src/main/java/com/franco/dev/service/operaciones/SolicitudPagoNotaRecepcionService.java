package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.repository.operaciones.NotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoNotaRecepcionRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SolicitudPagoNotaRecepcionService extends CrudService<SolicitudPagoNotaRecepcion, SolicitudPagoNotaRecepcionRepository, Long> {
    
    private final SolicitudPagoNotaRecepcionRepository repository;
    
    @Autowired
    private SolicitudPagoRepository solicitudPagoRepository;
    
    @Autowired
    private NotaRecepcionRepository notaRecepcionRepository;

    @Override
    public SolicitudPagoNotaRecepcionRepository getRepository() {
        return repository;
    }

    @Override
    public SolicitudPagoNotaRecepcion save(SolicitudPagoNotaRecepcion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    /**
     * Add a nota recepcion to a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     * @param notaId The ID of the nota recepcion
     * @param monto The amount to include
     * @return The created relationship
     */
    @Transactional
    public SolicitudPagoNotaRecepcion agregarNotaASolicitud(Long solicitudId, Long notaId, Double monto) {
        // Validate that the relationship doesn't already exist
        if (repository.existsByNotaRecepcionId(notaId)) {
            throw new IllegalArgumentException("La nota de recepción ya está incluida en otra solicitud de pago");
        }
        
        // Load entities
        SolicitudPago solicitud = solicitudPagoRepository.findById(solicitudId)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudId));
        
        NotaRecepcion nota = notaRecepcionRepository.findById(notaId)
            .orElseThrow(() -> new IllegalArgumentException("Nota de recepción no encontrada: " + notaId));
        
        // Validate that the nota belongs to the same proveedor
        if (!nota.getPedido().getProveedor().getId().equals(solicitud.getProveedor().getId())) {
            throw new IllegalArgumentException("La nota de recepción debe pertenecer al mismo proveedor");
        }
        
        // Create relationship
        SolicitudPagoNotaRecepcion relacion = new SolicitudPagoNotaRecepcion();
        relacion.setSolicitudPago(solicitud);
        relacion.setNotaRecepcion(nota);
        relacion.setMontoIncluido(monto);
        relacion.setCreadoEn(LocalDateTime.now());
        
        return save(relacion);
    }
    
    /**
     * Remove a nota recepcion from a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     * @param notaId The ID of the nota recepcion
     * @return true if removed successfully
     */
    @Transactional
    public Boolean removerNotaDeSolicitud(Long solicitudId, Long notaId) {
        repository.deleteBySolicitudPagoIdAndNotaRecepcionId(solicitudId, notaId);
        
        // Recalculate total amount for the solicitud
        recalcularMontoTotalSolicitud(solicitudId);
        
        return true;
    }
    
    /**
     * Get all nota recepcion relationships for a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     * @return List of relationships
     */
    public List<SolicitudPagoNotaRecepcion> getNotasDeSolicitud(Long solicitudId) {
        return repository.findBySolicitudPagoId(solicitudId);
    }

    /**
     * Obtiene las notas de una solicitud con NotaRecepcion cargada (para evitar LazyInitializationException).
     * Usado en impresión de ticket donde se accede a numero y fecha de cada nota.
     */
    public List<SolicitudPagoNotaRecepcion> getNotasDeSolicitudWithNotaRecepcion(Long solicitudId) {
        return repository.findBySolicitudPagoIdWithNotaRecepcion(solicitudId);
    }
    
    /**
     * Get all solicitud pago relationships for a nota recepcion
     * @param notaId The ID of the nota recepcion
     * @return List of relationships
     */
    public List<SolicitudPagoNotaRecepcion> getSolicitudesDeNota(Long notaId) {
        return repository.findByNotaRecepcionId(notaId);
    }
    
    /**
     * Check if a nota recepcion is already included in any solicitud pago
     * @param notaId The ID of the nota recepcion
     * @return true if already included
     */
    public Boolean isNotaIncludedInSolicitud(Long notaId) {
        return repository.existsByNotaRecepcionId(notaId);
    }
    
    /**
     * Get total amount for a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     * @return The total amount
     */
    public Double getTotalMontoForSolicitud(Long solicitudId) {
        return repository.getTotalMontoForSolicitudPago(solicitudId);
    }
    
    /**
     * Delete all relationships for a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     */
    @Transactional
    public void eliminarTodasRelaciones(Long solicitudId) {
        repository.deleteBySolicitudPagoId(solicitudId);
    }
    
    /**
     * Recalculate total amount for a solicitud pago
     * @param solicitudId The ID of the solicitud pago
     */
    @Transactional
    public void recalcularMontoTotalSolicitud(Long solicitudId) {
        Double nuevoTotal = getTotalMontoForSolicitud(solicitudId);
        
        SolicitudPago solicitud = solicitudPagoRepository.findById(solicitudId)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud de pago no encontrada: " + solicitudId));
        
        solicitud.setMontoTotal(nuevoTotal);
        solicitudPagoRepository.save(solicitud);
    }
}