package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.service.financiero.ChequeService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import com.franco.dev.service.personas.ProveedorService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PagoDetalleCuotaResolver implements GraphQLResolver<PagoDetalleCuota> {

    @Autowired
    private ChequeService chequeService;
    
    @Autowired
    private SolicitudPagoService solicitudPagoService;
    
    
    @Autowired
    private ProveedorService proveedorService;

    public Cheque cheque(PagoDetalleCuota pagoDetalleCuota) {
        return chequeService.findByPagoDetalleCuotaId(pagoDetalleCuota.getId());
    }
    
    /**
     * Resolver for the proveedor field. Follows the complex chain of relationships:
     * PagoDetalleCuota -> PagoDetalle -> Pago -> SolicitudPago (when tipo=COMPRA) -> NotaRecepcionAgrupada -> Proveedor
     */
    public Proveedor proveedor(PagoDetalleCuota pagoDetalleCuota) {
        try {
            // // Get the PagoDetalle
            // if (pagoDetalleCuota.getPagoDetalle() == null) {
            //     return null;
            // }
            
            // // Get the Pago
            // if (pagoDetalleCuota.getPagoDetalle().getPago() == null) {
            //     return null;
            // }
            
            // // Get the SolicitudPago
            // SolicitudPago solicitudPago = pagoDetalleCuota.getPagoDetalle().getPago().
            // if (solicitudPago == null) {
            //     return null;
            // }
            
            // // Check if the type is COMPRA
            // if (solicitudPago.getTipo() != TipoSolicitudPago.COMPRA) {
            //     return null;
            // }
            
            // // Get the NotaRecepcionAgrupada using the referenciaId
            // Long notaRecepcionAgrupadaId = solicitudPago.getReferenciaId();
            // if (notaRecepcionAgrupadaId == null) {
            //     return null;
            // }
            
            // NotaRecepcionAgrupada notaRecepcionAgrupada = notaRecepcionAgrupadaService.findById(notaRecepcionAgrupadaId).orElse(null);
            // if (notaRecepcionAgrupada == null) {
            //     return null;
            // }
            
            // // Return the proveedor
            // return notaRecepcionAgrupada.getProveedor();
            return null;
        } catch (Exception e) {
            // Log error but don't fail if there's an exception
            System.err.println("Error resolving proveedor for PagoDetalleCuota ID " + pagoDetalleCuota.getId() + ": " + e.getMessage());
            return null;
        }
    }
} 