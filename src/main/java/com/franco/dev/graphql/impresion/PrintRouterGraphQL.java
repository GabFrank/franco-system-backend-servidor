package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.empresarial.Impresora;
import com.franco.dev.service.empresarial.ImpresoraService;
import com.franco.dev.service.impresion.PrintRouterService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Endpoints de impresion con routing (Fase 3).
 */
@Component
public class PrintRouterGraphQL implements GraphQLMutationResolver {

    @Autowired
    private PrintRouterService printRouterService;

    @Autowired
    private ImpresoraService impresoraService;

    /**
     * HOJA del routing: ejecuta la impresion LOCAL en este host. Lo invoca el proxy
     * desde otro host; no vuelve a rutear (evita loops).
     */
    public Boolean imprimirEnDestino(Long impresoraId, String payloadBase64) {
        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        return printRouterService.imprimirLocalPorId(impresoraId, payload);
    }

    /**
     * ENTRADA con routing: imprime local si la impresora es de este host o reenvia al
     * host dueño.
     */
    public Boolean imprimirEnImpresora(Long impresoraId, String payloadBase64) {
        byte[] payload = Base64.getDecoder().decode(payloadBase64);
        return printRouterService.imprimir(impresoraId, payload);
    }

    /**
     * Imprime un ticket de prueba (generado server-side, respetando el ancho de la
     * impresora) y lo rutea al host correspondiente.
     */
    public Boolean imprimirPruebaEnImpresora(Long impresoraId) {
        Impresora impresora = impresoraService.findById(impresoraId).orElse(null);
        if (impresora == null) {
            return false;
        }
        byte[] payload = printRouterService.generarTicketPrueba(impresora);
        return printRouterService.imprimir(impresoraId, payload);
    }
}
