package com.franco.dev.service.sifen;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.NotaCredito;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.personas.ClienteGraphQL;
import com.franco.dev.graphql.personas.ClienteResponse;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.util.SifenReceptorDesdeXml;
import com.roshka.sifen.core.fields.request.de.TgDatRec;
import com.roshka.sifen.core.types.PaisType;
import com.roshka.sifen.core.types.TiNatRec;
import com.roshka.sifen.core.types.TiTiOpe;
import com.roshka.sifen.core.types.TiTipDocRec;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Receptor del exterior en el central (#666): la NC copia el B2F de la factura y un pasaporte no se busca como RUC. */
class ReceptorExtranjeroCentralTest {

    private static final String CDC = "01800123456001001000000122026091712345678901";

    static String xmlFactura(int iTiOpe) {
        return "<rDE xmlns=\"http://ekuatia.set.gov.py/sifen/xsd\"><DE Id=\"" + CDC + "\"><gDatGralOpe>"
                + "<gDatRec><iNatRec>2</iNatRec><iTiOpe>" + iTiOpe + "</iTiOpe><cPaisRec>BRA</cPaisRec>"
                + "<dDesPaisRe>Brasil</dDesPaisRe><dNomRec>JOAO DA SILVA</dNomRec><dDirRec>RUA 1</dDirRec>"
                + "<dNumCasRec>0</dNumCasRec><iTipIDRec>" + TiTipDocRec.PASAPORTE.getVal() + "</iTipIDRec>"
                + "<dDTipIDRec>Pasaporte</dDTipIDRec><dNumIDRec>FX987654</dNumIDRec></gDatRec>"
                + "</gDatGralOpe></DE></rDE>";
    }

    @Test
    void elReceptorB2FSeLeeDelXmlDeLaFactura() {
        TgDatRec r = SifenReceptorDesdeXml.receptorB2F(xmlFactura(4));
        assertEquals(TiTiOpe.B2F, r.getiTiOpe());
        assertEquals(TiNatRec.NO_CONTRIBUYENTE, r.getiNatRec());
        assertEquals(PaisType.BRA, r.getcPaisRec());
        assertEquals(TiTipDocRec.PASAPORTE, r.getiTipIDRec());
        assertEquals("FX987654", r.getdNumIDRec());
        assertEquals("JOAO DA SILVA", r.getdNomRec());
        assertEquals("RUA 1", r.getdDirRec());
    }

    @Test
    void unaFacturaQueNoEsB2FOUnXmlIlegibleNoCambianNada() {
        assertNull(SifenReceptorDesdeXml.receptorB2F(xmlFactura(2)));
        assertNull(SifenReceptorDesdeXml.receptorB2F("<no-cierra"));
        assertNull(SifenReceptorDesdeXml.receptorB2F(null));
    }

    private SifenService service(EstadoEvento nominacion, String xml) {
        SifenService s = new ObjenesisStd().newInstance(SifenService.class);
        EventoNominacionDEService eventos = mock(EventoNominacionDEService.class);
        if (nominacion != null) {
            EventoNominacionDE e = new EventoNominacionDE();
            e.setEstado(nominacion);
            when(eventos.findActivosByCdcDocumento(CDC)).thenReturn(Collections.singletonList(e));
        }
        DocumentoElectronicoService des = mock(DocumentoElectronicoService.class);
        DocumentoElectronico de = new DocumentoElectronico();
        de.setXmlOriginal(xml);
        when(des.findByFacturaLegalId(5L, 2L)).thenReturn(Optional.of(de));
        ReflectionTestUtils.setField(s, "eventoNominacionDEService", eventos);
        ReflectionTestUtils.setField(s, "documentoElectronicoService", des);
        return s;
    }

    private static NotaCredito nota() {
        NotaCredito n = new NotaCredito();
        n.setFacturaLegalId(5L);
        n.setSucursalId(2L);
        return n;
    }

    @Test
    void laNotaDeCreditoUsaElReceptorB2FDeLaFactura() {
        TgDatRec r = service(null, xmlFactura(4)).receptorB2FDeLaFactura(nota(), CDC);
        assertEquals(PaisType.BRA, r.getcPaisRec());
        assertEquals(TiTiOpe.B2F, r.getiTiOpe());
        // una nominacion rechazada no cambio el receptor
        assertNotNull(service(EstadoEvento.RECHAZADO, xmlFactura(4)).receptorB2FDeLaFactura(nota(), CDC));
    }

    @Test
    void conUnaNominacionAprobadaVaElSnapshotDeLaNota() {
        assertNull(service(EstadoEvento.APROBADO, xmlFactura(4)).receptorB2FDeLaFactura(nota(), CDC));
        assertNull(service(null, xmlFactura(2)).receptorB2FDeLaFactura(nota(), CDC));
    }

    private ClienteGraphQL resolver(ClienteService clientes) {
        ClienteGraphQL r = new ObjenesisStd().newInstance(ClienteGraphQL.class);
        ReflectionTestUtils.setField(r, "service", clientes);
        return r;
    }

    @Test
    void unPasaporteSeBuscaTalCualSinSetNiAlta() {
        ClienteService clientes = mock(ClienteService.class);
        Cliente joao = new Cliente();
        joao.setId(9L);
        when(clientes.findByPersonaDocumento("FX987654")).thenReturn(joao);
        ClienteGraphQL r = resolver(clientes);

        assertSame(joao, r.clientePorPersonaDocumento(" FX987654 "));
        ClienteResponse detallado = r.clientePorPersonaDocumentoDetallado("FX987654");
        assertSame(joao, detallado.getCliente());
        assertTrue(detallado.getExito());
        ClienteResponse otro = r.clientePorPersonaDocumentoDetallado("AB-1");
        assertFalse(otro.getExito());
        verify(clientes, never()).buscarOCrearClientePorDocumento(anyString());
    }

    @Test
    void unPasaporteNoSeConsultaEnSet() {
        SifenConsultaRucService set = new ObjenesisStd().newInstance(SifenConsultaRucService.class);
        ReflectionTestUtils.setField(set, "sifenConfig", new com.roshka.sifen.core.SifenConfig());
        assertNull(set.consultaRuc("FX987654"));
    }
}
