package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.rrhh.LiquidacionFinal;
import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalConcepto;
import com.franco.dev.domain.rrhh.enums.LiquidacionFinalEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.utilitarios.NumeroALetrasService;
import com.franco.dev.utilitarios.print.ReciboTicketEscPosTest;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Recibo de un solo item de liquidacion (sueldo y finiquito): HABER da recibo, DESCUENTO da
 * constancia, con la liquidacion en BORRADOR, APROBADA o PAGADA (no ANULADA). Se mira el texto de lo que sale.
 */
class ReciboItemLiquidacionTest {

    private LiquidacionItemRepository itemRepository;
    private LiquidacionFinalItemRepository finalItemRepository;
    private ReporteRrhhService reportes;

    @BeforeEach
    void setUp() {
        itemRepository = mock(LiquidacionItemRepository.class);
        finalItemRepository = mock(LiquidacionFinalItemRepository.class);
        ConfiguracionGeneralService configGeneral = mock(ConfiguracionGeneralService.class);
        when(configGeneral.findAll2()).thenReturn(Collections.emptyList());
        NumeroALetrasService letras = mock(NumeroALetrasService.class);
        when(letras.converter(anyString(), anyBoolean())).thenReturn("CIEN MIL");
        ConfiguracionRrhhService configRrhh = mock(ConfiguracionRrhhService.class);
        when(configRrhh.getString(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        reportes = new ReporteRrhhService(mock(LiquidacionSueldoRepository.class), configRrhh,
                mock(LiquidacionFinalService.class), mock(ValeRepository.class), mock(PrestamoRepository.class),
                mock(AguinaldoRepository.class), mock(PenalizacionRepository.class), mock(BonoRepository.class),
                letras, configGeneral, mock(CiudadService.class), itemRepository, finalItemRepository);
    }

    @Test
    void haberDeSueldoAprobadaEnLosTresFormatos() throws Exception {
        itemSueldo(21L, LiquidacionSueldoEstado.APROBADA, LiquidacionItemTipo.HABER, "BONO POR VENTAS", "100000");

        String ticket = ticket(reportes.reciboItemLiquidacionBase64(21L, 80, true));
        assertTrue(ticket.contains("RECIBO DE LIQUIDACION Nro. 486"), ticket);
        assertTrue(ticket.contains("BONO POR VENTAS"), ticket);
        assertTrue(ticket.contains("100.000") || ticket.contains("100,000"), ticket);
        assertTrue(ticket.contains("Recibi conforme, en concepto de BONO POR VENTAS"), ticket);
        assertTrue(ticket.contains("Liquidacion de sueldo 2026-09"), ticket);

        for (Integer ancho : new Integer[]{null, 58, 80}) {
            String pdf = textoPdf(reportes.reciboItemLiquidacionBase64(21L, ancho, false));
            assertTrue(pdf.contains("RECIBO DE LIQUIDACION Nro. 486"), "PDF " + ancho + ": " + pdf);
            assertTrue(pdf.contains("BONO POR VENTAS"), "PDF " + ancho + ": " + pdf);
        }
    }

    @Test
    void descuentoDeSueldoPagadaEsConstancia() throws Exception {
        itemSueldo(22L, LiquidacionSueldoEstado.PAGADA, LiquidacionItemTipo.DESCUENTO, "DESCUENTO IPS", "250000");

        String ticket = ticket(reportes.reciboItemLiquidacionBase64(22L, 58, true));
        assertTrue(ticket.contains("CONSTANCIA DE DESCUENTO"), ticket);
        assertTrue(ticket.contains("Tomo conocimiento y acepto el descuento"), ticket);
        assertFalse(ticket.contains("Recibi conforme"), ticket);
        // Una constancia no lleva parentesis: el monto es lo que se descuenta.
        assertFalse(ticket.contains("(250"), ticket);

        String a4 = textoPdf(reportes.reciboItemLiquidacionBase64(22L, null, false));
        assertTrue(a4.contains("CONSTANCIA DE DESCUENTO Nro. 486"), a4);
        assertTrue(a4.contains("Tomo conocimiento"), a4);
    }

    @Test
    void sueldoAnuladaNoGeneraRecibo() {
        itemSueldo(23L, LiquidacionSueldoEstado.ANULADA, LiquidacionItemTipo.HABER, "SALARIO BASE", "3100000");
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> reportes.reciboItemLiquidacionBase64(23L, null, false));
        assertTrue(e.getMessage().contains("ANULADA"), e.getMessage());
    }

    /** En BORRADOR tambien se emite. La observacion no lleva el estado. */
    @Test
    void sueldoEnBorradorGeneraRecibo() {
        itemSueldo(27L, LiquidacionSueldoEstado.BORRADOR, LiquidacionItemTipo.HABER, "SALARIO BASE", "3100000");
        String ticket = ticket(reportes.reciboItemLiquidacionBase64(27L, 80, true));
        assertTrue(ticket.contains("RECIBO DE LIQUIDACION Nro. 486"), ticket);
        assertTrue(ticket.contains("Obs.: Liquidacion de sueldo 2026-09"), ticket);
        assertFalse(ticket.contains("BORRADOR"), ticket);
    }

    @Test
    void itemInexistenteOSinLiquidacion() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> reportes.reciboItemLiquidacionBase64(99L, null, false));

        LiquidacionItem suelto = new LiquidacionItem();
        suelto.setId(98L);
        suelto.setMonto(new BigDecimal("1000"));
        when(itemRepository.findById(98L)).thenReturn(Optional.of(suelto));
        assertThrows(GraphQLException.class, () -> reportes.reciboItemLiquidacionBase64(98L, null, false));
    }

    @Test
    void montoCeroONuloNoGeneraRecibo() {
        itemSueldo(24L, LiquidacionSueldoEstado.APROBADA, LiquidacionItemTipo.HABER, "BONO", "0");
        assertThrows(GraphQLException.class, () -> reportes.reciboItemLiquidacionBase64(24L, 80, true));

        LiquidacionItem it = itemSueldo(25L, LiquidacionSueldoEstado.APROBADA, LiquidacionItemTipo.HABER, "BONO", "1");
        it.setMonto(null);
        assertThrows(GraphQLException.class, () -> reportes.reciboItemLiquidacionBase64(25L, 80, true));
    }

    @Test
    void sueldoSinDescripcionUsaElCodigo() {
        LiquidacionItem it = itemSueldo(26L, LiquidacionSueldoEstado.APROBADA, LiquidacionItemTipo.HABER, null, "5000");
        it.setCodigo("BONO_MANUAL");
        String ticket = ticket(reportes.reciboItemLiquidacionBase64(26L, 80, true));
        assertTrue(ticket.contains("BONO MANUAL"), ticket);
    }

    @Test
    void finiquitoAprobadaSinDescripcionUsaElConcepto() throws Exception {
        itemFinal(31L, LiquidacionFinalEstado.APROBADA, null);

        String ticket = ticket(reportes.reciboItemLiquidacionFinalBase64(31L, 80, true));
        assertTrue(ticket.contains("RECIBO DE LIQUIDACION Nro. 7"), ticket);
        assertTrue(ticket.contains("Obs.: Liquidacion final"), ticket);
        assertTrue(ticket.contains("VACACIONES NO GOZADAS"), ticket);

        String a4 = textoPdf(reportes.reciboItemLiquidacionFinalBase64(31L, null, false));
        assertTrue(a4.contains("VACACIONES NO GOZADAS"), a4);
    }

    @Test
    void finiquitoAnuladoNoGeneraRecibo() {
        itemFinal(32L, LiquidacionFinalEstado.ANULADA, "SALARIO DEL MES");
        assertThrows(GraphQLException.class, () -> reportes.reciboItemLiquidacionFinalBase64(32L, null, false));
    }

    @Test
    void finiquitoEnBorradorGeneraRecibo() {
        itemFinal(33L, LiquidacionFinalEstado.BORRADOR, "SALARIO DEL MES");
        String ticket = ticket(reportes.reciboItemLiquidacionFinalBase64(33L, 80, true));
        assertTrue(ticket.contains("Obs.: Liquidacion final"), ticket);
        assertFalse(ticket.contains("BORRADOR"), ticket);
    }

    // ===== helpers =====

    private LiquidacionItem itemSueldo(Long id, LiquidacionSueldoEstado estado, LiquidacionItemTipo tipo,
                                       String descripcion, String monto) {
        LiquidacionSueldo liq = new LiquidacionSueldo();
        liq.setId(486L);
        liq.setPeriodo("2026-09");
        liq.setEstado(estado);
        liq.setFuncionario(funcionario());
        LiquidacionItem it = new LiquidacionItem();
        it.setId(id);
        it.setLiquidacion(liq);
        it.setTipo(tipo);
        it.setDescripcion(descripcion);
        it.setMonto(new BigDecimal(monto));
        when(itemRepository.findById(id)).thenReturn(Optional.of(it));
        return it;
    }

    private void itemFinal(Long id, LiquidacionFinalEstado estado, String descripcion) {
        LiquidacionFinal lf = new LiquidacionFinal();
        lf.setId(7L);
        lf.setEstado(estado);
        lf.setFuncionario(funcionario());
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setId(id);
        it.setLiquidacionFinal(lf);
        it.setConcepto(LiquidacionFinalConcepto.VACACIONES_NO_GOZADAS);
        it.setDescripcion(descripcion);
        it.setTipo(LiquidacionItemTipo.HABER);
        it.setMonto(new BigDecimal("1200000"));
        when(finalItemRepository.findById(id)).thenReturn(Optional.of(it));
    }

    private static Funcionario funcionario() {
        Persona p = new Persona();
        p.setNombre("JUAN PEREZ");
        p.setDocumento("1234567");
        Funcionario f = new Funcionario();
        f.setPersona(p);
        return f;
    }

    private static String ticket(String base64) {
        // Las lineas del ticket se envuelven: se juntan con espacio para buscar frases enteras.
        return String.join(" ", ReciboTicketEscPosTest.lineas(base64)).replaceAll("\\s+", " ");
    }

    /** Texto de todas las paginas del PDF, con los saltos de linea como espacios. */
    private static String textoPdf(String base64) throws Exception {
        PdfReader r = new PdfReader(Base64.getDecoder().decode(base64));
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= r.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(r, i)).append(' ');
        r.close();
        return sb.toString().replaceAll("\\s+", " ");
    }
}
