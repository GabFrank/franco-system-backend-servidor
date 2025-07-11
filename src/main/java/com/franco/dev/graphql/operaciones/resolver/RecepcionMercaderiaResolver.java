package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionCostoAdicional;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.operaciones.RecepcionCostoAdicionalService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecepcionMercaderiaResolver implements GraphQLResolver<RecepcionMercaderia> {

    @Autowired
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    @Autowired
    private RecepcionCostoAdicionalService recepcionCostoAdicionalService;

    public Proveedor proveedor(RecepcionMercaderia recepcionMercaderia) {
        return recepcionMercaderia.getProveedor();
    }

    public Sucursal sucursalRecepcion(RecepcionMercaderia recepcionMercaderia) {
        return recepcionMercaderia.getSucursalRecepcion();
    }

    public Moneda moneda(RecepcionMercaderia recepcionMercaderia) {
        return recepcionMercaderia.getMoneda();
    }

    public Usuario usuario(RecepcionMercaderia recepcionMercaderia) {
        return recepcionMercaderia.getUsuario();
    }

    /**
     * Resuelve los ítems de la recepción de mercadería
     */
    public List<RecepcionMercaderiaItem> items(RecepcionMercaderia recepcionMercaderia) {
        return recepcionMercaderiaItemService.findByRecepcionMercaderiaId(recepcionMercaderia.getId());
    }

    /**
     * Resuelve los costos adicionales de la recepción
     */
    public List<RecepcionCostoAdicional> costosAdicionales(RecepcionMercaderia recepcionMercaderia) {
        return recepcionCostoAdicionalService.findByRecepcionMercaderiaId(recepcionMercaderia.getId());
    }
} 