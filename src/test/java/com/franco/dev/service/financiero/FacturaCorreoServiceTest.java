package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.DocumentoElectronico;
import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.EstadoDE;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.empresarial.SucursalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El KuDE va al correo que el cliente tiene guardado, una sola vez; sin un correo valido no se
 * envia nada.
 */
class FacturaCorreoServiceTest {

    private static final long FACTURA = 535067L;
    private static final long SUCURSAL = 1L;

    private JdbcTemplate jdbc;
    private FacturaLegalService facturaLegalService;
    private DocumentoElectronicoService documentoElectronicoService;
    private SucursalService sucursalService;
    private FacturaLegalGraphQL facturaLegalGraphQL;
    private JavaMailSender sender;
    private MimeMessage mensaje;
    private FacturaCorreoService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        facturaLegalService = mock(FacturaLegalService.class);
        documentoElectronicoService = mock(DocumentoElectronicoService.class);
        sucursalService = mock(SucursalService.class);
        facturaLegalGraphQL = mock(FacturaLegalGraphQL.class);
        sender = mock(JavaMailSender.class);
        mensaje = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mensaje);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);

        service = new FacturaCorreoService(jdbc, facturaLegalService, documentoElectronicoService,
                sucursalService, facturaLegalGraphQL, provider, mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "mailHost", "smtp.ejemplo.com");
        ReflectionTestUtils.setField(service, "mailUsername", "facturas@ejemplo.com");
        ReflectionTestUtils.setField(service, "remitente", "");
        ReflectionTestUtils.setField(service, "ventanaHoras", 48);
        ReflectionTestUtils.setField(service, "maxIntentos", 6);
        ReflectionTestUtils.setField(service, "reintentoMinutos", 10);
        ReflectionTestUtils.setField(service, "lote", 20);

        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(new long[]{FACTURA, SUCURSAL}));
        Sucursal sucursal = new Sucursal();
        sucursal.setCodigoEstablecimientoFactura("021");
        when(sucursalService.findById(SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(facturaLegalGraphQL.generarPdfFacturaElectronica(FACTURA, SUCURSAL)).thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void enviaElKudeAlCorreoDelCliente() throws Exception {
        factura("FINANZAS2@GMAIL.COM.PY");
        documento(EstadoDE.APROBADO);

        assertEquals(1, service.enviarPendientes());

        verify(sender).send(mensaje);
        assertEquals("finanzas2@gmail.com.py", mensaje.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("Factura electrónica 021-001-0025127 - FRANCO AREVALOS S.A.", mensaje.getSubject());
        assertTrue(mensaje.getFrom()[0].toString().contains("facturas@ejemplo.com"));
        assertEquals(List.of("Factura-021-001-0025127.pdf", "Factura-021-001-0025127.xml"), adjuntos());
        verify(jdbc).update(anyString(), eq(FACTURA), eq(SUCURSAL), eq("finanzas2@gmail.com.py"),
                eq("ENVIADO"), eq(null), eq("ENVIADO"));
    }

    @Test
    void unCorreoQueNoEsUnaDireccionNoSeEnvia() {
        factura("WW");
        documento(EstadoDE.APROBADO);

        assertEquals(0, service.enviarPendientes());

        verify(sender, never()).send(any(MimeMessage.class));
        verify(jdbc).update(anyString(), eq(FACTURA), eq(SUCURSAL), eq("ww"), eq("OMITIDO"), anyString(),
                eq("OMITIDO"));
    }

    @Test
    void sinCorreoGuardadoNoSeEnviaNiSeRegistra() {
        factura("  ");
        documento(EstadoDE.APROBADO);

        assertEquals(0, service.enviarPendientes());

        verify(sender, never()).send(any(MimeMessage.class));
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void siElDocumentoDejoDeEstarAprobadoNoSeEnvia() {
        factura("finanzas2@gmail.com.py");
        documento(EstadoDE.CANCELADO);

        assertEquals(0, service.enviarPendientes());

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void siFallaElServidorDeCorreoQuedaEnErrorParaReintentar() {
        factura("finanzas2@gmail.com.py");
        documento(EstadoDE.APROBADO);
        doThrow(new MailSendException("conexion rechazada")).when(sender).send(any(MimeMessage.class));

        assertEquals(0, service.enviarPendientes());

        verify(jdbc).update(anyString(), eq(FACTURA), eq(SUCURSAL), eq("finanzas2@gmail.com.py"),
                eq("ERROR"), eq("conexion rechazada"), eq("ERROR"));
    }

    @Test
    void sinServidorDeCorreoConfiguradoNoBuscaFacturas() {
        ReflectionTestUtils.setField(service, "mailHost", "");

        assertEquals(0, service.enviarPendientes());

        verify(facturaLegalService, never()).findByIdAndSucursalId(any(), any());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elEnvioManualVaALaDireccionIndicadaAunqueElClienteNoTengaCorreo() throws Exception {
        factura(null);
        documento(EstadoDE.APROBADO);

        assertTrue(service.enviarManual(FACTURA, SUCURSAL, " Diego@Ejemplo.com "));

        verify(sender).send(mensaje);
        assertEquals("diego@ejemplo.com", mensaje.getRecipients(Message.RecipientType.TO)[0].toString());
        verify(jdbc).update(anyString(), eq(FACTURA), eq(SUCURSAL), eq("diego@ejemplo.com"),
                eq("ENVIADO"), eq(null), eq("ENVIADO"));
    }

    @Test
    void elEnvioManualRechazaUnaDireccionInvalida() {
        factura(null);
        documento(EstadoDE.APROBADO);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.enviarManual(FACTURA, SUCURSAL, "diego@"));

        assertEquals("El correo no es una dirección válida", e.getMessage());
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void elEnvioManualAvisaSiElServidorDeCorreoFalla() {
        factura(null);
        documento(EstadoDE.APROBADO);
        doThrow(new MailSendException("conexion rechazada")).when(sender).send(any(MimeMessage.class));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.enviarManual(FACTURA, SUCURSAL, "diego@ejemplo.com"));

        assertEquals("No se pudo enviar el correo: conexion rechazada", e.getMessage());
    }

    @Test
    void elEnvioManualNoMandaUnaElectronicaSinAprobar() {
        factura(null).getTimbradoDetalle().getTimbrado().setIsElectronico(true);
        documento(EstadoDE.RECHAZADO);

        assertThrows(GraphQLException.class, () -> service.enviarManual(FACTURA, SUCURSAL, "diego@ejemplo.com"));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    private FacturaLegal factura(String email) {
        Persona persona = new Persona();
        persona.setEmail(email);
        Cliente cliente = new Cliente();
        cliente.setPersona(persona);
        Timbrado timbrado = new Timbrado();
        timbrado.setRazonSocial("FRANCO AREVALOS S.A.");
        TimbradoDetalle detalle = new TimbradoDetalle();
        detalle.setTimbrado(timbrado);
        detalle.setPuntoExpedicion("001");
        FacturaLegal factura = new FacturaLegal();
        factura.setCliente(cliente);
        factura.setNombre("TRAX S.A.");
        factura.setNumeroFactura(25127);
        factura.setFecha(LocalDateTime.of(2026, 10, 1, 0, 31));
        factura.setTimbradoDetalle(detalle);
        when(facturaLegalService.findByIdAndSucursalId(FACTURA, SUCURSAL)).thenReturn(factura);
        return factura;
    }

    private void documento(EstadoDE estado) {
        DocumentoElectronico de = new DocumentoElectronico();
        de.setEstado(estado);
        de.setCdc("01800994825021001002512722026100113573223296");
        de.setXmlOriginal("<rDE/>");
        when(documentoElectronicoService.findByFacturaLegalId(FACTURA, SUCURSAL)).thenReturn(Optional.of(de));
    }

    private List<String> adjuntos() throws Exception {
        List<String> nombres = new ArrayList<>();
        Multipart partes = (Multipart) mensaje.getContent();
        for (int i = 0; i < partes.getCount(); i++) {
            if (partes.getBodyPart(i).getFileName() != null) {
                nombres.add(partes.getBodyPart(i).getFileName());
            }
        }
        return nombres;
    }
}
