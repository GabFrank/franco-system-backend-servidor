package com.franco.dev.graphql.financiero;

import org.springframework.beans.factory.annotation.Autowired;
import com.franco.dev.service.financiero.FacturaCorreoService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.stereotype.Component;

/**
 * Envio manual de una factura por correo, desde el listado de facturas del desktop.
 *
 * <p>Va en un resolver aparte y no en {@link FacturaLegalGraphQL} porque
 * {@link FacturaCorreoService} ya depende de ese para generar el PDF: seria un ciclo.
 *
 * <p>Implementa {@code GraphQLQueryResolver} aunque no tenga queries: el chequeo de login de
 * {@code SecurityGraphQLAspect} solo alcanza a los beans de ese tipo.
 */
@Component
public class FacturaCorreoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private com.franco.dev.service.financiero.TesoreriaSecurityService seg;

    private final FacturaCorreoService facturaCorreoService;

    public FacturaCorreoGraphQL(FacturaCorreoService facturaCorreoService) {
        this.facturaCorreoService = facturaCorreoService;
    }

    public Boolean enviarFacturaLegalPorCorreo(Long id, Long sucId, String email) {
        seg.requireAnyRole("ANALISIS DE CAJA");
        return facturaCorreoService.enviarManual(id, sucId, email);
    }
}
