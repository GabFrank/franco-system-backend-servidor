package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.EventoNominacionDE;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.enums.EstadoEvento;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.service.financiero.DocumentoElectronicoService;
import com.franco.dev.service.financiero.EventoNominacionDEService;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.personas.ClienteService;
import com.franco.dev.service.sifen.SifenEventoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #433: nominar desde «Editar factura legal» fallaba siempre. El diálogo guardaba el cliente antes de
 * nominar y nominarFacturaElectronica rechazaba porque la factura ya tenía cliente. Ahora «ya nominada»
 * se lee del XML enviado a SIFEN, y la respuesta sale del evento que quedó (antes devolvía true aunque
 * SIFEN rechazara).
 */
class NominarFacturaElectronicaTest {

    private static final Long FACTURA = 300L;
    private static final Long SUCURSAL = 1L;
    private static final Long CLIENTE = 3837L;
    private static final String CDC = "01800000000000000000000000000000000000000000";
    private static final String XML_INNOMINADO = "<gDatRec><iNatRec>2</iNatRec><iTipIDRec>5</iTipIDRec>"
            + "<dDTipIDRec>Innominado</dDTipIDRec><dNumIDRec>0</dNumIDRec><dNomRec>Sin Nombre</dNomRec></gDatRec>";
    private static final String XML_NOMINADO = "<gDatRec><iNatRec>1</iNatRec><dRucRec>80061439</dRucRec>"
            + "<dDVRec>9</dDVRec><dNomRec>AVOX S.A</dNomRec></gDatRec>";

    @Mock private FacturaLegalService service;
    @Mock private DocumentoElectronicoService documentoElectronicoService;
    @Mock private ClienteService clienteService;
    @Mock private SifenEventoService sifenEventoService;
    @Mock private EventoNominacionDEService eventoNominacionDEService;

    @InjectMocks private FacturaLegalGraphQL resolver;

    private AutoCloseable mocks;
    private FacturaLegal factura;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        factura = new FacturaLegal();
        factura.setId(FACTURA);
        factura.setSucursalId(SUCURSAL);
        factura.setCdc(CDC);
        factura.setTotalFinal(150000.0);
        when(service.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura);
        when(clienteService.findById(CLIENTE)).thenReturn(Optional.of(cliente("MAURO RIVAS", "5466642")));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static Cliente cliente(String nombre, String documento) {
        Persona persona = new Persona();
        persona.setNombre(nombre);
        persona.setDocumento(documento);
        Cliente cliente = new Cliente();
        cliente.setId(CLIENTE);
        cliente.setPersona(persona);
        cliente.setTributa(false);
        return cliente;
    }

    private void emitidaCon(String xml) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setXmlFirmado(xml);
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.of(de));
    }

    private void quedaEvento(EstadoEvento estado) {
        EventoNominacionDE viejo = new EventoNominacionDE();
        viejo.setId(1L);
        viejo.setEstado(EstadoEvento.RECHAZADO);
        EventoNominacionDE nuevo = new EventoNominacionDE();
        nuevo.setId(2L);
        nuevo.setEstado(estado);
        nuevo.setMensajeRespuesta("CDC inexistente");
        when(eventoNominacionDEService.findActivosByCdcDocumento(CDC)).thenReturn(Arrays.asList(viejo, nuevo));
    }

    @Test
    void innominadaSinClienteSeNominaYDevuelveTrueSiSifenAprueba() throws Exception {
        emitidaCon(XML_INNOMINADO);
        quedaEvento(EstadoEvento.APROBADO);

        assertTrue(resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        verify(sifenEventoService).nominarReceptor(any(), any());
    }

    @Test
    void conClienteGuardadoPeroXmlInnominadoIgualSeNomina() throws Exception {
        // Las del cliente 0 «SIN NOMBRE» y las que el bug dejó con cliente sin nominar en SIFEN
        factura.setCliente(cliente("SIN NOMBRE", "X"));
        emitidaCon(XML_INNOMINADO);
        quedaEvento(EstadoEvento.APROBADO);

        assertTrue(resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        verify(sifenEventoService).nominarReceptor(any(), any());
    }

    @Test
    void emitidaNominadaNoSeVuelveANominar() throws Exception {
        emitidaCon(XML_NOMINADO);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        assertTrue(e.getMessage().contains("ya está nominada"), e.getMessage());
        verify(sifenEventoService, never()).nominarReceptor(anyString(), any());
    }

    @Test
    void noSeNominaAUnClienteSinNombre() throws Exception {
        emitidaCon(XML_INNOMINADO);
        when(clienteService.findById(CLIENTE)).thenReturn(Optional.of(cliente("SIN NOMBRE", "X")));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        assertTrue(e.getMessage().contains("cliente identificado"), e.getMessage());
        verify(sifenEventoService, never()).nominarReceptor(anyString(), any());
    }

    @Test
    void rechazoDeSifenSaleComoErrorConElMotivo() throws Exception {
        emitidaCon(XML_INNOMINADO);
        quedaEvento(EstadoEvento.RECHAZADO);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        assertTrue(e.getMessage().contains("CDC inexistente"), e.getMessage());
    }

    @Test
    void errorDeEnvioTambienSaleComoError() {
        emitidaCon(XML_INNOMINADO);
        quedaEvento(EstadoEvento.ERROR_ENVIO);

        assertThrows(RuntimeException.class, () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
    }

    @Test
    void pendienteDevuelveFalse() throws Exception {
        emitidaCon(XML_INNOMINADO);
        quedaEvento(EstadoEvento.PENDIENTE);

        assertFalse(resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
    }

    @Test
    void sinDocumentoElectronicoNoSeNomina() throws Exception {
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
        verify(sifenEventoService, never()).nominarReceptor(anyString(), any());
    }

    @Test
    void elXmlConPrefijoDeNamespaceTambienSeReconoce() {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setXmlOriginal("<ns2:iTipIDRec>5</ns2:iTipIDRec>");
        assertTrue(FacturaLegalGraphQL.emitidoInnominado(de));
        de.setXmlOriginal("<iTipIDRec>1</iTipIDRec>");
        assertFalse(FacturaLegalGraphQL.emitidoInnominado(de));
        assertFalse(FacturaLegalGraphQL.emitidoInnominado(new DocumentoElectronico()));
    }

    @Test
    void sinEventoTrasElEnvioEsError() {
        emitidaCon(XML_INNOMINADO);
        when(eventoNominacionDEService.findActivosByCdcDocumento(CDC)).thenReturn(Collections.emptyList());

        assertThrows(RuntimeException.class, () -> resolver.nominarFacturaElectronica(FACTURA, SUCURSAL, CLIENTE));
    }
}
