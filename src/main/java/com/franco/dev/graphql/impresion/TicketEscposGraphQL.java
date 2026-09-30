package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.impresion.ImpresionService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

/**
 * Impresion desde el cliente contra el central ("Imprimir desde esta PC" en el desktop): genera el
 * comprobante en ESC/POS y lo devuelve en base64 para que el desktop lo imprima en su impresora
 * local. Cada tipo es el mismo papel que ya imprime el central:
 * <ul>
 *   <li>FACTURA: "Reimprimir" de la lista de facturas (reimprimirFacturaLegal).</li>
 *   <li>BALANCE: "Imprimir Cierre" de la caja (imprimirBalance).</li>
 * </ul>
 * Nunca busca impresora (printerName va null). Devuelve null si el renderer no escribio nada y lanza
 * si lo pedido no existe. Las impresiones por servidor no pasan por aca y no cambian.
 * Ver docs/impresion-desde-cliente.md.
 */
@Component
public class TicketEscposGraphQL implements GraphQLQueryResolver {

    @Autowired private FacturaLegalService facturaLegalService;
    @Autowired private FacturaLegalItemService facturaLegalItemService;
    @Autowired private FacturaLegalGraphQL facturaLegalGraphQL;
    @Autowired private PdvCajaService pdvCajaService;
    @Autowired private ImpresionService impresionService;

    public String ticketEscpos(TicketEscposTipo tipo, Long id, Long sucId, String local) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (tipo) {
            case FACTURA: {
                FacturaLegal facturaLegal = facturaLegalService.findByIdAndSucursalId(id, sucId);
                if (facturaLegal == null) {
                    throw noExiste("La factura", id, sucId);
                }
                List<FacturaLegalItem> items = facturaLegalItemService.findByFacturaLegalId(id, sucId);
                facturaLegalGraphQL.printTicket58mmFactura(facturaLegal.getVenta(), facturaLegal, items, null, out);
                break;
            }
            case BALANCE: {
                PdvCaja caja = pdvCajaService.getRepository().findByIdAndSucursalId(id, sucId);
                if (caja == null) {
                    throw noExiste("La caja", id, sucId);
                }
                impresionService.printBalance(pdvCajaService.generarBalance(caja), null, local, out);
                break;
            }
        }
        return out.size() > 0 ? Base64.getEncoder().encodeToString(out.toByteArray()) : null;
    }

    private static GraphQLException noExiste(String que, Long id, Long sucId) {
        return new GraphQLException(que + " " + id + " (sucursal " + sucId + ") no existe");
    }
}
