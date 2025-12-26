package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPagoNotaRecepcion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolicitudPagoNotaRecepcionRepository extends HelperRepository<SolicitudPagoNotaRecepcion, Long>, JpaSpecificationExecutor<SolicitudPagoNotaRecepcion> {
    
    default Class<SolicitudPagoNotaRecepcion> getEntityClass() {
        return SolicitudPagoNotaRecepcion.class;
    }
    
    // Find all nota recepcion relationships for a specific solicitud pago
    List<SolicitudPagoNotaRecepcion> findBySolicitudPagoId(Long solicitudPagoId);
    
    // Find all solicitud pago relationships for a specific nota recepcion
    List<SolicitudPagoNotaRecepcion> findByNotaRecepcionId(Long notaRecepcionId);
    
    // Check if a specific relationship already exists
    Optional<SolicitudPagoNotaRecepcion> findBySolicitudPagoIdAndNotaRecepcionId(Long solicitudPagoId, Long notaRecepcionId);
    
    // Check if a nota recepcion is already included in any solicitud pago
    boolean existsByNotaRecepcionId(Long notaRecepcionId);
    
    // Get total amount for a solicitud pago
    @Query("SELECT COALESCE(SUM(spnr.montoIncluido), 0.0) FROM SolicitudPagoNotaRecepcion spnr WHERE spnr.solicitudPago.id = :solicitudPagoId")
    Double getTotalMontoForSolicitudPago(@Param("solicitudPagoId") Long solicitudPagoId);
    
    // Delete all relationships for a solicitud pago
    void deleteBySolicitudPagoId(Long solicitudPagoId);
    
    // Delete a specific relationship
    void deleteBySolicitudPagoIdAndNotaRecepcionId(Long solicitudPagoId, Long notaRecepcionId);
}