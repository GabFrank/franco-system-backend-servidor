package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.RecepcionMercaderia;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionCostoAdicional;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaNota;
import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.RecepcionMercaderiaItemService;
import com.franco.dev.service.operaciones.RecepcionCostoAdicionalService;
import com.franco.dev.service.operaciones.RecepcionMercaderiaNotaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolver para RecepcionMercaderia
 * 
 * Usado en:
 * - Desktop: No
 * - Mobile: Sí (componente histórico de recepción de mercaderías)
 */
@Component
public class RecepcionMercaderiaResolver implements GraphQLResolver<RecepcionMercaderia> {

    @Autowired
    private RecepcionMercaderiaItemService recepcionMercaderiaItemService;

    @Autowired
    private RecepcionCostoAdicionalService recepcionCostoAdicionalService;

    @Autowired
    private RecepcionMercaderiaNotaService recepcionMercaderiaNotaService;

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

    /**
     * Resuelve las notas de recepción asociadas a la recepción de mercadería
     * 
     * Usado en:
     * - Desktop: No
     * - Mobile: Sí (para calcular cantNotas en el componente histórico)
     */
    public List<NotaRecepcion> notas(RecepcionMercaderia recepcionMercaderia) {
        List<RecepcionMercaderiaNota> asociaciones = recepcionMercaderiaNotaService
            .findByRecepcionMercaderiaId(recepcionMercaderia.getId());
        return asociaciones.stream()
            .map(RecepcionMercaderiaNota::getNotaRecepcion)
            .collect(Collectors.toList());
    }
} 