package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.*;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.repository.rrhh.*;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.utilitarios.NumeroALetrasService;
import com.franco.dev.utilitarios.print.ReciboTicketEscPosTest;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Todos los recibos firmables de RRHH llevan el numero (id) del registro, y los que tienen
 * observacion la imprimen. Se mira el texto de lo que sale: el ticket ESC/POS decodificado
 * y el texto del PDF.
 */
class RecibosRrhhNumeroObservacionTest {

    private ValeRepository valeRepository;
    private PenalizacionRepository penalizacionRepository;
    private AguinaldoRepository aguinaldoRepository;
    private PrestamoRepository prestamoRepository;
    private BonoRepository bonoRepository;
    private LiquidacionFinalService liquidacionFinalService;
    private LiquidacionSueldoService liquidacionSueldoService;
    private ReporteRrhhService reportes;
    private ReciboLiquidacionService reciboLiquidacion;

    @BeforeEach
    void setUp() {
        valeRepository = mock(ValeRepository.class);
        penalizacionRepository = mock(PenalizacionRepository.class);
        aguinaldoRepository = mock(AguinaldoRepository.class);
        prestamoRepository = mock(PrestamoRepository.class);
        bonoRepository = mock(BonoRepository.class);
        liquidacionFinalService = mock(LiquidacionFinalService.class);
        liquidacionSueldoService = mock(LiquidacionSueldoService.class);
        ConfiguracionGeneralService configGeneral = mock(ConfiguracionGeneralService.class);
        when(configGeneral.findAll2()).thenReturn(Collections.emptyList());
        NumeroALetrasService letras = mock(NumeroALetrasService.class);
        when(letras.converter(anyString(), anyBoolean())).thenReturn("CIEN MIL");
        ConfiguracionRrhhService configRrhh = mock(ConfiguracionRrhhService.class);
        when(configRrhh.getString(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        reportes = new ReporteRrhhService(mock(LiquidacionSueldoRepository.class), configRrhh,
                liquidacionFinalService, valeRepository, prestamoRepository, aguinaldoRepository,
                penalizacionRepository, bonoRepository, letras, configGeneral, mock(CiudadService.class));
        reciboLiquidacion = new ReciboLiquidacionService(liquidacionSueldoService, configGeneral, letras,
                valeRepository, bonoRepository, mock(VacacionVentaRepository.class),
                mock(PrestamoCuotaRepository.class), penalizacionRepository, configRrhh,
                mock(LiquidacionConceptoService.class));
    }

    @Test
    void valeConNumeroYObservacionEnTicketYEnPdf() throws Exception {
        Vale v = new Vale();
        v.setId(12L);
        v.setMonto(new BigDecimal("100000"));
        v.setFecha(LocalDate.of(2026, 9, 10));
        v.setEsAdelanto(true);
        MotivoVale m = new MotivoVale();
        m.setNombre("ANTICIPO SUELDO");
        v.setMotivo(m);
        v.setObservacion("PAGO DEL COLEGIO");
        when(valeRepository.findById(12L)).thenReturn(Optional.of(v));

        String ticket = String.join("\n", ReciboTicketEscPosTest.lineas(reportes.reciboValeBase64(12L, 80, true)));
        assertTrue(ticket.contains("RECIBO DE ADELANTO Nro. 12"), ticket);
        assertTrue(ticket.contains("ANTICIPO SUELDO (2026-09-10)"), ticket);
        assertTrue(ticket.contains("Obs.: PAGO DEL COLEGIO"), ticket);

        for (Integer ancho : new Integer[]{null, 58, 80}) {
            String pdf = textoPdf(reportes.reciboValeBase64(12L, ancho, false));
            assertTrue(pdf.contains("Nro. 12"), "PDF " + ancho + ": " + pdf);
            assertTrue(pdf.contains("PAGO DEL COLEGIO"), "PDF " + ancho + ": " + pdf);
        }
    }

    @Test
    void valeSinObservacionNoImprimeLaLinea() {
        Vale v = new Vale();
        v.setId(13L);
        v.setMonto(new BigDecimal("50000"));
        v.setObservacion("   ");
        when(valeRepository.findById(13L)).thenReturn(Optional.of(v));
        String ticket = String.join("\n", ReciboTicketEscPosTest.lineas(reportes.reciboValeBase64(13L, 58, true)));
        assertTrue(ticket.contains("RECIBO DE VALE Nro. 13"), ticket);
        assertFalse(ticket.contains("Obs."), ticket);
    }

    @Test
    void losDemasRecibosLlevanNumero() {
        Penalizacion p = new Penalizacion();
        p.setId(5L);
        p.setMonto(new BigDecimal("20000"));
        when(penalizacionRepository.findById(5L)).thenReturn(Optional.of(p));
        assertTicketContiene(reportes.reciboPenalizacionBase64(5L, 80, true), "RECIBO DE PENALIZACION Nro. 5");

        Aguinaldo a = new Aguinaldo();
        a.setId(3L);
        a.setMontoCalculado(new BigDecimal("3100000"));
        when(aguinaldoRepository.findById(3L)).thenReturn(Optional.of(a));
        assertTicketContiene(reportes.reciboAguinaldoBase64(3L, 80, true), "RECIBO DE AGUINALDO Nro. 3");

        Bono b = new Bono();
        b.setId(4L);
        b.setMonto(new BigDecimal("80000"));
        when(bonoRepository.findById(4L)).thenReturn(Optional.of(b));
        assertTicketContiene(reportes.reciboBonoBase64(4L, 80, true), "RECIBO DE BONO Nro. 4");
    }

    @Test
    void prestamoConNumeroYObservacion() {
        Prestamo p = new Prestamo();
        p.setId(9L);
        p.setMontoTotal(new BigDecimal("600000"));
        p.setObservacion("DESCONTAR DESDE OCTUBRE");
        when(prestamoRepository.findById(9L)).thenReturn(Optional.of(p));
        assertTicketContiene(reportes.reciboPrestamoBase64(9L, 80, true),
                "RECIBO DE PRESTAMO Nro. 9", "Obs.: DESCONTAR DESDE OCTUBRE");
    }

    @Test
    void finiquitoConNumeroYObservacionEnTicketYEnA4() throws Exception {
        LiquidacionFinal lf = new LiquidacionFinal();
        lf.setId(7L);
        lf.setTotalLiquidado(new BigDecimal("2500000"));
        lf.setObservacion("RENUNCIA VOLUNTARIA");
        when(liquidacionFinalService.findById(7L)).thenReturn(Optional.of(lf));
        LiquidacionFinalItem it = new LiquidacionFinalItem();
        it.setDescripcion("SALARIO DEL MES");
        it.setMonto(new BigDecimal("2500000"));
        it.setTipo(LiquidacionItemTipo.HABER);
        when(liquidacionFinalService.findItems(7L)).thenReturn(Collections.singletonList(it));

        assertTicketContiene(reportes.finiquitoBase64(7L, 80, true),
                "LIQUIDACION FINAL Nro. 7", "Obs.: RENUNCIA VOLUNTARIA");
        String a4 = textoPdf(reportes.finiquitoBase64(7L, null, false));
        assertTrue(a4.contains("Nro. 7") && a4.contains("RENUNCIA VOLUNTARIA"), a4);
    }

    @Test
    void liquidacionDeSueldoConNumeroYObservacionEnLosTresFormatos() throws Exception {
        LiquidacionSueldo liq = new LiquidacionSueldo();
        liq.setId(486L);
        liq.setPeriodo("2026-09");
        liq.setTotalHaberes(new BigDecimal("3100000"));
        liq.setTotalNeto(new BigDecimal("3100000"));
        liq.setObservacion("PAGO POR TRANSFERENCIA");
        when(liquidacionSueldoService.findById(486L)).thenReturn(Optional.of(liq));
        LiquidacionItem it = new LiquidacionItem();
        it.setCodigo("SALARIO_BASE");
        it.setDescripcion("SALARIO BASE");
        it.setMonto(new BigDecimal("3100000"));
        it.setTipo(LiquidacionItemTipo.HABER);
        when(liquidacionSueldoService.findItems(486L)).thenReturn(Collections.singletonList(it));

        assertTicketContiene(reciboLiquidacion.generarBase64(486L, 80, true),
                "RECIBO DE SUELDO 2026-09 Nro. 486", "Obs.: PAGO POR TRANSFERENCIA");
        String ticketPdf = textoPdf(reciboLiquidacion.generarBase64(486L, 80, false));
        assertTrue(ticketPdf.contains("Nro. 486") && ticketPdf.contains("PAGO POR TRANSFERENCIA"), ticketPdf);
        String a4 = textoPdf(reciboLiquidacion.generarBase64(486L, null, false));
        assertTrue(a4.contains("Nro. 486") && a4.contains("PAGO POR TRANSFERENCIA"), a4);
    }

    /** El A4 de sueldo tiene alto fijo: la observacion larga se recorta ahi; el ticket la imprime entera. */
    @Test
    void liquidacionConObservacionLargaSeRecortaSoloEnA4() throws Exception {
        StringBuilder larga = new StringBuilder();
        for (int i = 0; larga.length() < 600; i++) larga.append("TRAMO").append(i).append(' ');
        String obs = larga.toString().trim();
        LiquidacionSueldo liq = new LiquidacionSueldo();
        liq.setId(487L);
        liq.setPeriodo("2026-09");
        liq.setObservacion(obs);
        when(liquidacionSueldoService.findById(487L)).thenReturn(Optional.of(liq));
        when(liquidacionSueldoService.findItems(487L)).thenReturn(Collections.emptyList());

        String a4 = textoPdf(reciboLiquidacion.generarBase64(487L, null, false));
        String ultimo = obs.substring(obs.lastIndexOf(' ') + 1);
        assertTrue(a4.contains("..."), "el A4 tiene que marcar el recorte: " + a4);
        assertFalse(a4.contains(ultimo), "el A4 no deberia llegar al final [" + ultimo + "]: " + a4);

        String ticket = String.join(" ", ReciboTicketEscPosTest.lineas(reciboLiquidacion.generarBase64(487L, 80, true)));
        assertTrue(ticket.contains(ultimo), "el ticket imprime la observacion entera: " + ticket);
    }

    // ===== helpers =====

    private static void assertTicketContiene(String base64, String... esperados) {
        List<String> lineas = ReciboTicketEscPosTest.lineas(base64);
        String texto = String.join("\n", lineas);
        for (String e : esperados) assertTrue(texto.contains(e), "falta [" + e + "] en el ticket:\n" + texto);
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
