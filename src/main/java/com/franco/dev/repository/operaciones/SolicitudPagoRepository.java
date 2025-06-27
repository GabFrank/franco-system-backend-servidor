package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface SolicitudPagoRepository extends HelperRepository<SolicitudPago, Long>, JpaSpecificationExecutor<SolicitudPago> {
    default Class<SolicitudPago> getEntityClass() {
        return SolicitudPago.class;
    }

    public List<SolicitudPago> findByUsuarioId(Long id);
    
    public SolicitudPago findByTipoAndReferenciaId(TipoSolicitudPago tipo, Long referenciaId);
    
    public List<SolicitudPago> findByPagoId(Long pagoId);

    // Using basic query for simple filters
    Page<SolicitudPago> findByReferenciaIdAndTipoAndEstadoAndCreadoEnBetween(
            Long referenciaId, 
            TipoSolicitudPago tipo, 
            SolicitudPagoEstado estado, 
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Pageable pageable);
}

