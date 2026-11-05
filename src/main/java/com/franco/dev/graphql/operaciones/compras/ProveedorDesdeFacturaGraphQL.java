package com.franco.dev.graphql.operaciones.compras;

import com.franco.dev.service.operaciones.compras.ProveedorDesdeFacturaService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dar de alta al proveedor con los datos de su factura (QR / CDC o factura en papel), cuando el RUC no es de
 * ningún proveedor cargado.
 *
 * Usado en: Desktop no · Mobile sí (módulo Compras).
 */
@Component
public class ProveedorDesdeFacturaGraphQL implements GraphQLMutationResolver {

    @Autowired
    private ProveedorDesdeFacturaService service;

    public ProveedorDeFacturaMobile proveedorDeFacturaMobile(String qr, Long documentoId) {
        return service.datos(qr, documentoId);
    }

    public ProveedorDeFacturaMobile registrarProveedorDeFacturaMobile(RegistrarProveedorFacturaInput input) {
        return service.registrar(input);
    }
}
