package com.franco.dev.graphql.impresion;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.financiero.FacturaLegalItemService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.impresion.ImpresionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.OutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ticketEscpos del central genera, para imprimir desde el cliente, el mismo comprobante que
 * "Reimprimir" (factura) e "Imprimir Cierre" (balance), escrito en memoria y en base64. Nunca pide
 * impresora: printerName va siempre null.
 */
class TicketEscposGraphQLTest {

    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalItemService facturaLegalItemService;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private PdvCajaRepository pdvCajaRepository;
    @Mock private ImpresionService impresionService;
    @InjectMocks private TicketEscposGraphQL resolver;

    private static final byte[] TICKET = {0x1b, 0x40, 'O', 'K', 0x0a};
    private static final String TICKET_B64 = Base64.getEncoder().encodeToString(TICKET);

    private static Answer<Object> escribeEnArgumento(int n) {
        return inv -> {
            ((OutputStream) inv.getArgument(n)).write(TICKET);
            return true;
        };
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(pdvCajaService.getRepository()).thenReturn(pdvCajaRepository);
    }

    @Test
    void facturaEsLaMismaQueReimprimir() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(513827L);
        List<FacturaLegalItem> items = Collections.singletonList(new FacturaLegalItem());
        when(facturaLegalService.findByIdAndSucursalId(513827L, 1L)).thenReturn(factura);
        when(facturaLegalItemService.findByFacturaLegalId(513827L, 1L)).thenReturn(items);
        doAnswer(escribeEnArgumento(4)).when(facturaLegalGraphQL).printTicket58mmFactura(any(), any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.FACTURA, 513827L, 1L, null));
        verify(facturaLegalGraphQL).printTicket58mmFactura(isNull(), same(factura), same(items), isNull(),
                any(OutputStream.class));
    }

    @Test
    void balanceEsElMismoQueImprimirCierre() throws Exception {
        PdvCaja caja = new PdvCaja();
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        when(pdvCajaRepository.findByIdAndSucursalId(7166L, 1L)).thenReturn(caja);
        when(pdvCajaService.generarBalance(caja)).thenReturn(balance);
        doAnswer(escribeEnArgumento(3)).when(impresionService).printBalance(any(), any(), any(),
                any(OutputStream.class));

        assertEquals(TICKET_B64, resolver.ticketEscpos(TicketEscposTipo.BALANCE, 7166L, 1L, "CAJA 1"));
        verify(impresionService).printBalance(same(balance), isNull(), eq("CAJA 1"), any(OutputStream.class));
    }

    @Test
    void siNoExisteFallaConMensaje() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver.ticketEscpos(TicketEscposTipo.FACTURA, 99L, 1L, null));
        assertTrue(e.getMessage().contains("99"));
    }

    @Test
    void siElRendererNoEscribeNadaDevuelveNull() throws Exception {
        PdvCaja caja = new PdvCaja();
        when(pdvCajaRepository.findByIdAndSucursalId(7166L, 1L)).thenReturn(caja);
        when(pdvCajaService.generarBalance(caja)).thenReturn(new PdvCajaBalanceDto());
        assertNull(resolver.ticketEscpos(TicketEscposTipo.BALANCE, 7166L, 1L, null));
    }
}
