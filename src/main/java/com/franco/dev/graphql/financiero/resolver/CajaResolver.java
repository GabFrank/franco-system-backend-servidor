package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.GastoDetalle;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.GastoDetalleService;
import com.franco.dev.service.financiero.PdvCajaService;
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

    private final Logger log = LoggerFactory.getLogger(CajaResolver.class);

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

    public CajaBalance balance(PdvCaja e){
        return pdvCajaService.getBalance(new EmbebedPrimaryKey(e.getId(), e.getSucursalId()));
    }

    public Sucursal sucursal(PdvCaja e){
        return e.getSucursalId()!= null ? sucursalService.findById(e.getSucursalId()).orElse(null) : null;
    }

}
