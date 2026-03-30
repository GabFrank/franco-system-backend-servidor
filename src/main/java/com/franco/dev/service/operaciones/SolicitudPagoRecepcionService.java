package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoRecepcion;
import com.franco.dev.repository.operaciones.SolicitudPagoRecepcionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class SolicitudPagoRecepcionService extends CrudService<SolicitudPagoRecepcion, SolicitudPagoRecepcionRepository, Long> {
    
    private final SolicitudPagoRecepcionRepository repository;

    @Override
    public SolicitudPagoRecepcionRepository getRepository() {
        return repository;
    }

    /**
     * Busca asociaciones por ID de solicitud de pago
     */
    public List<SolicitudPagoRecepcion> findBySolicitudPagoId(Long solicitudPagoId) {
        return repository.findBySolicitudPagoId(solicitudPagoId);
    }

    /**
     * Busca asociaciones por ID de recepción de mercadería
     */
    public List<SolicitudPagoRecepcion> findByRecepcionMercaderiaId(Long recepcionId) {
        return repository.findByRecepcionMercaderiaId(recepcionId);
    }

    /**
     * Asocia una recepción de mercadería a una solicitud de pago
     */
    @Transactional
    public SolicitudPagoRecepcion asociarRecepcionASolicitud(SolicitudPago solicitudPago, 
                                                            RecepcionMercaderia recepcion, 
                                                            Double montoAsignado) {
        // Crear la asociación
        SolicitudPagoRecepcion asociacion = new SolicitudPagoRecepcion();
        asociacion.setSolicitudPago(solicitudPago);
        asociacion.setRecepcionMercaderia(recepcion);
        // TODO: Implementar setMontoAsignado cuando se defina el campo en la entidad
        
        return save(asociacion);
    }
} 