package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.domain.financiero.enums.MotivoAnulacionCheque;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.utilitarios.print.ChequeAnuladoTicketEscPos;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChequeAnuladoTicketServiceTest {

    private static String texto(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.ISO_8859_1);
    }

    private static Cheque anulado() {
        Moneda gs = new Moneda(); gs.setSimbolo("Gs"); gs.setDecimales(0);
        Banco banco = new Banco(); banco.setNombre("BANCO CONTINENTAL");
        CuentaBancaria cuenta = new CuentaBancaria(); cuenta.setNumero("1234567"); cuenta.setBanco(banco);
        Usuario u = new Usuario(); u.setNickname("MR");
        Cheque c = new Cheque();
        c.setId(5L); c.setNumero(390001.0); c.setEstado(EstadoCheque.ANULADO);
        c.setCuentaBancaria(cuenta); c.setMoneda(gs); c.setTotal(1500000.0); c.setDiferido(true);
        c.setBeneficiario("DISTRIBUIDORA EL SOL SOCIEDAD ANONIMA COMERCIAL E INDUSTRIAL");
        c.setFechaEntrega(LocalDateTime.of(2026, 11, 20, 9, 0));
        c.setFechaPago(LocalDateTime.of(2026, 12, 20, 0, 0));
        c.setMotivoAnulacionTipo(MotivoAnulacionCheque.MALA_IMPRESION);
        c.setObservacionAnulacion("sin tinta\nen la fecha");
        c.setFechaAnulacion(LocalDateTime.of(2026, 11, 25, 15, 30));
        c.setUsuarioAnulacion(u);
        return c;
    }

    @Test
    void el_ticket_trae_los_datos_del_cheque_y_de_la_anulacion() {
        String t = texto(ChequeAnuladoTicketEscPos.build(ChequeAnuladoTicketService.datos(anulado(), "FRANCO S.A."), 58));

        assertTrue(t.contains("CHEQUE ANULADO"), t);
        assertTrue(t.contains("FRANCO S.A."));
        assertTrue(t.contains("390001"));
        assertTrue(t.contains("BANCO CONTINENTAL"));
        assertTrue(t.contains("1234567"));
        assertTrue(t.contains("Gs 1.500.000"));
        assertTrue(t.contains("20/11/2026"));
        assertTrue(t.contains("20/12/2026 (diferido)"));
        assertTrue(t.contains("Mala impresion"), "sin acentos para la térmica");
        assertTrue(t.contains("sin tinta en la fecha"), "la observación en una línea");
        assertTrue(t.contains("25/11/2026 15:30"));
        assertTrue(t.contains("Por: MR"));
        assertTrue(t.contains("Reserva liberada"));
        assertTrue(t.contains("Firma responsable"));
        assertTrue(t.contains("INDUSTRIAL"), "el beneficiario largo se envuelve, no se corta");
    }

    @Test
    void las_lineas_de_texto_no_pasan_del_ancho_del_papel() {
        for (int mm : new int[]{58, 80}) {
            int cols = mm == 80 ? 48 : 32;
            String t = texto(ChequeAnuladoTicketEscPos.build(ChequeAnuladoTicketService.datos(anulado(), "FRANCO S.A."), mm));
            for (String linea : t.split("\n")) {
                // Lo que se imprime va después del último byte de control (los comandos ESC/POS terminan en uno).
                int corte = -1;
                for (int i = 0; i < linea.length(); i++) if (linea.charAt(i) < 0x20) corte = i;
                String visible = linea.substring(corte + 1);
                assertTrue(visible.length() <= cols, mm + "mm: «" + visible + "»");
            }
        }
    }

    @Test
    void un_cheque_anulado_antes_del_cambio_sale_con_su_texto_y_sin_dato() {
        Cheque c = new Cheque();
        c.setId(7L); c.setEstado(EstadoCheque.ANULADO); c.setMotivoAnulacion("ANULADO DESDE DASHBOARD");
        c.setMovimientoBancarioId(40L);

        String t = texto(ChequeAnuladoTicketEscPos.build(ChequeAnuladoTicketService.datos(c, null), 80));

        assertTrue(t.contains("ANULADO DESDE DASHBOARD"), t);
        assertTrue(t.contains("sin dato"));
        assertTrue(t.contains("Movimiento bancario revertido"), "con débito, se revirtió el movimiento");
        assertTrue(t.contains("N.:") && t.contains("7"), "sin número, el id");
    }

    @Test
    void el_monto_usa_los_decimales_de_la_moneda() {
        Moneda usd = new Moneda(); usd.setSimbolo("US$"); usd.setDecimales(2);
        assertEquals("US$ 1.250,50", ChequeAnuladoTicketService.monto(1250.5, usd));
        assertNull(ChequeAnuladoTicketService.monto(null, usd));
    }

    @Test
    void un_cheque_que_no_esta_anulado_no_tiene_comprobante() {
        ChequeRepository repo = mock(ChequeRepository.class);
        ConfiguracionGeneralService cg = mock(ConfiguracionGeneralService.class);
        when(cg.findAll2()).thenReturn(Collections.emptyList());
        Cheque c = anulado(); c.setEstado(EstadoCheque.DIFERIDO);
        when(repo.findById(5L)).thenReturn(Optional.of(c));
        ChequeAnuladoTicketService service = new ChequeAnuladoTicketService(repo, cg);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.ticketBase64(5L, 58));
        assertTrue(e.getMessage().contains("no está anulado"), e.getMessage());

        c.setEstado(EstadoCheque.ANULADO);
        assertNotNull(service.ticketBase64(5L, 58));
    }
}
