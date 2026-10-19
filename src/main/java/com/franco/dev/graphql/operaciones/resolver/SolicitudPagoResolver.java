package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Pago;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.service.operaciones.SolicitudPagoDetalleService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SolicitudPagoResolver implements GraphQLResolver<SolicitudPago> {

    @Autowired
    private SolicitudPagoDetalleService solicitudPagoDetalleService;

    @Autowired
    private com.franco.dev.repository.financiero.CajaVirtualRepository cajaVirtualRepository;

    /** Nombre de la caja asignada, sin exponer la caja: la solicitud la leen roles que no ven tesorería. */
    public String cajaVirtualAsignadaNombre(SolicitudPago solicitudPago) {
        Long cajaId = solicitudPago.getCajaVirtualAsignadaId();
        if (cajaId == null) return null;
        return cajaVirtualRepository.findById(cajaId)
                .map(com.franco.dev.domain.financiero.CajaVirtual::getNombre).orElse(null);
    }

    public Pago pago(SolicitudPago solicitudPago) {
        return solicitudPago.getPago();
    }

    /**
     * Resuelve la lista de detalles (formas de pago) por id de solicitud, sin lista en la entidad.
     */
    public List<SolicitudPagoDetalle> detalles(SolicitudPago solicitudPago) {
        return solicitudPagoDetalleService.findBySolicitudPagoId(solicitudPago.getId());
    }

    /**
     * Compatibilidad: si la entidad tiene formaPago null (tras refactor), devolver el del primer detalle.
     */
    public com.franco.dev.domain.financiero.FormaPago formaPago(SolicitudPago solicitudPago) {
        if (solicitudPago.getFormaPago() != null) {
            return solicitudPago.getFormaPago();
        }
        List<SolicitudPagoDetalle> dets = solicitudPagoDetalleService.findBySolicitudPagoId(solicitudPago.getId());
        return (dets != null && !dets.isEmpty()) ? dets.get(0).getFormaPago() : null;
    }

    /**
     * Compatibilidad: si la entidad tiene fechaPagoPropuesta null, devolver la del primer detalle.
     */
    public java.time.LocalDateTime fechaPagoPropuesta(SolicitudPago solicitudPago) {
        if (solicitudPago.getFechaPagoPropuesta() != null) {
            return solicitudPago.getFechaPagoPropuesta();
        }
        List<SolicitudPagoDetalle> dets = solicitudPagoDetalleService.findBySolicitudPagoId(solicitudPago.getId());
        if (dets != null && !dets.isEmpty() && dets.get(0).getFechaPago() != null) {
            return dets.get(0).getFechaPago().atStartOfDay();
        }
        return null;
    }
}

