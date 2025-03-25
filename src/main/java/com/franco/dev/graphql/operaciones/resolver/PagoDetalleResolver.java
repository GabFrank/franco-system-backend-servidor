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

    //here i need you to return sucursal form sucursal service usgin pagoDetalle.getCaja().getSucursalId()
    public Sucursal sucursal(PagoDetalle pagoDetalle) {
        //if pagoDetalle.getCaja() is null, return null
        if (pagoDetalle.getCaja() == null) {
            return null;
        }
        return sucursalService.findById(pagoDetalle.getCaja().getSucursalId()).orElse(null);
    }

    public List<PagoDetalleCuota> cuotasList(PagoDetalle pagoDetalle) {
        return pagoDetalleCuotaService.findByPagoDetalleId(pagoDetalle.getId());
    }
}

