package com.franco.dev.graphql.operaciones.compras;

import com.franco.dev.service.operaciones.compras.FacturaFotoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compras desde el celular (PWA): la factura en papel por foto, leída por IA. Después de confirmar la cabecera sigue
 * el mismo flujo que la factura electrónica ({@link ComprasMobileGraphQL}).
 *
 * Usado en: Desktop no · Mobile sí (módulo Compras).
 */
@Component
public class FacturaFotoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FacturaFotoService service;

    public String fotoFacturaMobile(Long documentoId, Integer orden) {
        return service.foto(documentoId, orden != null ? orden : 1);
    }

    public CabeceraFacturaFotoMobile leerFacturaFotoMobile(List<String> fotos, Long pedidoId) {
        return service.leer(fotos, pedidoId);
    }

    public FacturaProveedorMobile confirmarCabeceraFacturaFotoMobile(CabeceraFacturaFotoInput input) {
        return service.confirmarCabecera(input);
    }
}
