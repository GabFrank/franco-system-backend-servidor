package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.dto.ResultadoBusquedaLoteDto;
import com.franco.dev.service.operaciones.BusquedaLoteService;
import com.franco.dev.service.operaciones.InventarioSecurityService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Buscar lote por numero (#429, fase 2, entrega 1).
 *
 * Muestra a quien se vendio y que cajero lo hizo, asi que pide lo mismo que el Historial del lote
 * en el desktop: VER PRODUCTOS y ademas ANALISIS DE CAJA o ANALISIS DE VENTA (ADMIN pasa siempre).
 * Se valida aca, con el usuario de la sesion, y no solo en el menu.
 */
@Component
public class BusquedaLoteGraphQL implements GraphQLQueryResolver {

    static final String VER_PRODUCTOS = "VER PRODUCTOS";
    static final String[] ANALISIS = {"ANALISIS DE CAJA", "ANALISIS DE VENTA"};

    @Autowired
    private BusquedaLoteService service;

    @Autowired
    private InventarioSecurityService seguridad;

    public ResultadoBusquedaLoteDto buscarLotePorNumero(String numeroLote, Long productoId) {
        exigirPermisoConsultaLote();
        return service.buscarLotePorNumero(numeroLote, productoId);
    }

    void exigirPermisoConsultaLote() {
        if (!seguridad.hasAnyRole(VER_PRODUCTOS) || !seguridad.hasAnyRole(ANALISIS)) {
            throw new GraphQLException("No autorizado: se requiere VER PRODUCTOS y ademas "
                    + "ANALISIS DE CAJA o ANALISIS DE VENTA para consultar lotes.");
        }
    }
}
