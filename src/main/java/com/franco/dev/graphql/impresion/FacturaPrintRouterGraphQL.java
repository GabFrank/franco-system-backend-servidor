package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.impresion.PrintRouterService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

/**
 * "Imprimir en la sucursal" para facturas: aditivo, no reemplaza Reimprimir / Descargar PDF /
 * Abrir PDF. Reusa la generación de contenido existente (ticket y PDF) y la pasa por
 * {@link PrintRouterService}, que ya sabe rutear a la impresora elegida esté donde esté.
 */
@Component
public class FacturaPrintRouterGraphQL implements GraphQLMutationResolver {

    private static final Logger log = LoggerFactory.getLogger(FacturaPrintRouterGraphQL.class);

    @Autowired
    private FacturaLegalGraphQL facturaLegalGraphQL;

    @Autowired
    private FacturaLegalService facturaLegalService;

    @Autowired
    private FacturaLegalItemService facturaLegalItemService;

    @Autowired
    private PrintRouterService printRouterService;

    /** Genera el mismo PDF A4 que "Descargar PDF"/"Abrir PDF" y lo rutea a la impresora elegida. */
    public Boolean imprimirPdfFacturaEnImpresora(Long facturaId, Long sucId, Long impresoraId) {
        String base64 = facturaLegalGraphQL.descargarPdfFacturaElectronica(facturaId, sucId);
        if (base64 == null || base64.isBlank()) {
            log.warn("[PRINT] No se pudo generar el PDF de la factura id={} sucId={}", facturaId, sucId);
            return false;
        }
        byte[] pdfBytes = Base64.getDecoder().decode(base64);
        return printRouterService.imprimir(impresoraId, pdfBytes);
    }

    /** Genera el mismo ticket ESC/POS que "Reimprimir" (en memoria) y lo rutea a la impresora elegida. */
    public Boolean imprimirTicketFacturaEnImpresora(Long facturaId, Long sucId, Long impresoraId) {
        FacturaLegal facturaLegal = facturaLegalService.findByIdAndSucursalId(facturaId, sucId);
        if (facturaLegal == null) {
            log.warn("[PRINT] Factura id={} sucId={} no encontrada", facturaId, sucId);
            return false;
        }
        List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(facturaId, sucId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            facturaLegalGraphQL.printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, items, null, baos);
        } catch (Exception e) {
            log.error("[PRINT] Error generando ticket de factura id={}: {}", facturaId, e.getMessage(), e);
            return false;
        }
        return printRouterService.imprimir(impresoraId, baos.toByteArray());
    }
}
