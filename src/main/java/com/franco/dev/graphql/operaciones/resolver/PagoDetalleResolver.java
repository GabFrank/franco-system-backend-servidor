package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.PagoDetalle;
import com.franco.dev.domain.operaciones.PagoDetalleCuota;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.service.operaciones.PagoDetalleService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.PagoDetalleCuotaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PagoDetalleResolver implements GraphQLResolver<PagoDetalle> {

    @Autowired
    private PagoDetalleService pagoDetalleService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PagoDetalleCuotaService pagoDetalleCuotaService;

    public Sucursal sucursal(PagoDetalle pagoDetalle) {
        // First check if pagoDetalle has a caja with sucursalId
        if (pagoDetalle.getCaja() != null) {
            return sucursalService.findById(pagoDetalle.getCaja().getSucursalId()).orElse(null);
        }
        
        // If caja is null, try to get the sucursal_id from the database
        // We need to use a direct query since the relationship is not mapped in the entity
        try {
            // Use a native query to get the sucursal_id from the pago_detalle table
            Long sucursalId = pagoDetalleService.getSucursalIdForPagoDetalle(pagoDetalle.getId());
            if (sucursalId != null) {
                return sucursalService.findById(sucursalId).orElse(null);
            }
        } catch (Exception e) {
            // Log the error but continue
            System.err.println("Error retrieving sucursal for pagoDetalle " + pagoDetalle.getId() + ": " + e.getMessage());
        }
        
        return null;
    }

    public List<PagoDetalleCuota> cuotasList(PagoDetalle pagoDetalle) {
        return pagoDetalleCuotaService.findByPagoDetalleId(pagoDetalle.getId());
    }
}

