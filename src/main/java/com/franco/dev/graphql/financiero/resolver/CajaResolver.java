package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.CajaObservacion;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.CajaObservacionService;
import com.franco.dev.service.financiero.GastoDetalleService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import com.franco.dev.graphql.financiero.PdvCajaGraphQL;
import com.franco.dev.service.operaciones.VentaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class CajaResolver implements GraphQLResolver<PdvCaja> {

    @Autowired
    private PdvCajaService pdvCajaService;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private CajaObservacionService cajaObservacionService;

    @Autowired
    private TesoreriaSecurityService seg;

    private final Logger log = LoggerFactory.getLogger(CajaResolver.class);

    public List<CajaObservacion> cajaObservacionList(PdvCaja caja) {
        if (!PdvCajaGraphQL.puedeLeer(seg, caja)) return java.util.Collections.emptyList();
        return cajaObservacionService.findByPdvCajaIdAndSucursalId(caja.getId(), caja.getSucursalId());
    }

    /** Al que no es dueño ni tiene rol, null (#562): la caja anidada en una venta o un retiro sigue llegando. */
    public Conteo conteoApertura(PdvCaja e) {
        return PdvCajaGraphQL.puedeLeer(seg, e) ? e.getConteoApertura() : null;
    }

    /** Igual que la apertura, y además el cajero que compara su apertura con el cierre de la caja anterior. */
    public Conteo conteoCierre(PdvCaja e) {
        if (PdvCajaGraphQL.puedeLeer(seg, e) || PdvCajaGraphQL.esCajaAnteriorDeLaSesion(seg, pdvCajaService, e)) {
            return e.getConteoCierre();
        }
        return null;
    }

//    @Autowired
//    private CajaDetalleService cajaDetalleService;

//    public List<CajaDetalle> cajaDetalleList(Caja e){
//        return cajaDetalleService.findByCajaId(e.getId());
//    }

//    totalGs: Float
//    totalRs: Float
//    totalDs: Float
//    totalTarjeta: Float
//    totalDescuento: Float
//    totalAumento: Float
//    totalRetiro: Float
//    totalGasto: Float

    /** Al que no es dueño ni tiene rol, null y no error: un error descartaría la caja entera del resultado. */
    public CajaBalance balance(PdvCaja e){
        if (!PdvCajaGraphQL.puedeLeer(seg, e)) return null;
        return pdvCajaService.getBalance(new EmbebedPrimaryKey(e.getId(), e.getSucursalId()));
    }

    public Sucursal sucursal(PdvCaja e){
        return e.getSucursalId()!= null ? sucursalService.findById(e.getSucursalId()).orElse(null) : null;
    }

}
