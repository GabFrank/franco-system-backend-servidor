package com.franco.dev.service.operaciones.compras;

import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.operaciones.DocumentoRecibido;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.graphql.operaciones.compras.DatoDePersonaMobile;
import com.franco.dev.graphql.operaciones.compras.ProveedorDeFacturaMobile;
import com.franco.dev.graphql.operaciones.compras.RegistrarProveedorFacturaInput;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.repository.operaciones.DocumentoRecibidoRepository;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.ProveedorService;
import com.franco.dev.utilitarios.CalcularVerificadorRuc;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alta del proveedor con los datos de su factura (SIFEN o papel). */
class ProveedorDesdeFacturaServiceTest {

    @Mock private ComprasSecurityService seguridad;
    @Mock private ComprasMobileService compras;
    @Mock private DocumentoRecibidoService documentoRecibidoService;
    @Mock private DocumentoRecibidoRepository documentos;
    @Mock private PersonaService personaService;
    @Mock private ProveedorService proveedorService;
    @Mock private CiudadRepository ciudadRepository;
    @Mock private JdbcTemplate jdbc;
    @InjectMocks private ProveedorDesdeFacturaService service;
    private AutoCloseable mocks;

    private static final String RUC = "80012345";
    private static final String DV = String.valueOf(CalcularVerificadorRuc.getDigitoVerificador(RUC, 11));
    private static final String DV_MAL = String.valueOf((CalcularVerificadorRuc.getDigitoVerificador(RUC, 11) + 1) % 10);

    private static final String XML = "<rRetEnviDe><rDE><DE Id=\"0180012345\">"
            + "<gTimb><iTiDE>1</iTiDE></gTimb>"
            + "<gDatGralOpe><gEmis><dRucEm>" + RUC + "</dRucEm><dDVEmi>" + DV + "</dDVEmi>"
            + "<dNomEmi>DISTRIBUIDORA ACME S.A.</dNomEmi><dNomFanEmi>ACME</dNomFanEmi>"
            + "<dDirEmi>AVDA. ESPAÑA</dDirEmi><dNumCas>1234</dNumCas><dDesCiuEmi>ASUNCION (DISTRITO)</dDesCiuEmi>"
            + "<dTelEmi>021555666</dTelEmi><dEmailE>ventas@acme.com.py</dEmailE></gEmis></gDatGralOpe>"
            + "</DE></rDE></rRetEnviDe>";

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(personaService.save(any(Persona.class))).thenAnswer(inv -> {
            Persona p = inv.getArgument(0);
            if (p.getId() == null) p.setId(500L);
            return p;
        });
        when(proveedorService.save(any(Proveedor.class))).thenAnswer(inv -> {
            Proveedor p = inv.getArgument(0);
            if (p.getId() == null) p.setId(900L);
            return p;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static DocumentoRecibido sifen() {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setId(10L);
        d.setOrigen(DocumentoRecibido.ORIGEN_SIFEN);
        d.setRucEmisor(RUC);
        d.setDvEmisor(DV);
        d.setRazonSocialEmisor("DISTRIBUIDORA ACME S.A.");
        d.setCondicionOperacion(2);
        d.setXml(XML);
        return d;
    }

    private static DocumentoRecibido papel(String dv, String iaRespuesta) {
        DocumentoRecibido d = new DocumentoRecibido();
        d.setId(11L);
        d.setOrigen(DocumentoRecibido.ORIGEN_FOTO);
        d.setRucEmisor(RUC);
        d.setDvEmisor(dv);
        d.setRazonSocialEmisor("ACME PAPEL");
        d.setCondicionOperacion(1);
        d.setIaRespuesta(iaRespuesta);
        return d;
    }

    private static RegistrarProveedorFacturaInput input(Long documentoId) {
        RegistrarProveedorFacturaInput i = new RegistrarProveedorFacturaInput();
        i.setDocumentoId(documentoId);
        return i;
    }

    @Test
    void sifenPrecargaTodoElEmisorDelXml() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));

        ProveedorDeFacturaMobile d = service.datos(null, 10L);

        verify(seguridad).exigirCompras();
        assertEquals(RUC, d.getRuc());
        assertEquals(DV, d.getDv());
        assertTrue(d.getRucValido());
        assertEquals("DISTRIBUIDORA ACME S.A.", d.getNombre());
        assertEquals("ACME", d.getNombreFantasia());
        assertEquals("AVDA. ESPAÑA 1234", d.getDireccion());
        assertEquals("ASUNCION (DISTRITO)", d.getCiudad());
        assertEquals("021555666", d.getTelefono());
        assertEquals("ventas@acme.com.py", d.getEmail());
        assertTrue(d.getCredito());
        assertTrue(d.getPuedeRegistrar());
        assertNull(d.getProveedorId());
    }

    @Test
    void sinFacturaNoHaceNada() {
        assertThrows(GraphQLException.class, () -> service.datos(" ", null));
        verifyNoInteractions(documentoRecibidoService);
    }

    @Test
    void siYaEsProveedorNoSeOfreceElAlta() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Proveedor existente = new Proveedor();
        existente.setId(7L);
        Persona p = new Persona();
        p.setNombre("ACME VIEJO");
        existente.setPersona(p);
        when(compras.proveedorPorRuc(RUC)).thenReturn(existente);

        ProveedorDeFacturaMobile d = service.datos(null, 10L);

        assertEquals(7L, d.getProveedorId());
        assertFalse(d.getPuedeRegistrar());
        assertTrue(d.getMotivo().contains("ACME VIEJO"));
    }

    @Test
    void registrarCreaPersonaYProveedorConLosDatosDeLaFactura() {
        DocumentoRecibido doc = sifen();
        when(documentos.findById(10L)).thenReturn(Optional.of(doc));
        Ciudad asu = new Ciudad();
        asu.setId(1L);
        when(ciudadRepository.findByDescripcion("ASUNCION (DISTRITO)")).thenReturn(List.of(asu));
        RegistrarProveedorFacturaInput in = input(10L);
        in.setTelefono("0981 123 456"); // el usuario lo corrigió

        ProveedorDeFacturaMobile r = service.registrar(in);

        ArgumentCaptor<Persona> persona = ArgumentCaptor.forClass(Persona.class);
        verify(personaService).save(persona.capture());
        assertEquals(RUC, persona.getValue().getDocumento());
        assertEquals("DISTRIBUIDORA ACME S.A.", persona.getValue().getNombre());
        assertEquals("ACME", persona.getValue().getApodo());
        assertEquals("AVDA. ESPAÑA 1234", persona.getValue().getDireccion());
        assertEquals("0981 123 456", persona.getValue().getTelefono());
        assertEquals("ventas@acme.com.py", persona.getValue().getEmail());
        assertSame(asu, persona.getValue().getCiudad());

        ArgumentCaptor<Proveedor> proveedor = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedorService).save(proveedor.capture());
        assertSame(persona.getValue(), proveedor.getValue().getPersona());
        assertTrue(proveedor.getValue().getCredito());
        assertEquals(ProveedorDesdeFacturaService.CHEQUE_DIAS_POR_DEFECTO, proveedor.getValue().getChequeDias());
        assertNotNull(proveedor.getValue().getCreadoEn());

        assertEquals(900L, r.getProveedorId());
        assertFalse(r.getPuedeRegistrar());
        // Toma el lock por RUC antes de crear
        verify(jdbc).queryForList(contains("pg_advisory_xact_lock"), eq("proveedor-ruc:" + RUC));
    }

    @Test
    void registrarReutilizaLaPersonaExistenteSinPisarSusDatos() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Map<String, Object> fila = new HashMap<>();
        fila.put("id", 33L);
        fila.put("nombre", "CLIENTE ACME");
        when(jdbc.queryForList(contains("personas.persona"), eq(RUC))).thenReturn(List.of(fila));
        Persona cliente = new Persona();
        cliente.setId(33L);
        cliente.setDocumento(RUC);
        cliente.setNombre("CLIENTE ACME");
        cliente.setTelefono("0991000000");
        when(personaService.findById(33L)).thenReturn(Optional.of(cliente));

        service.registrar(input(10L));

        verify(personaService).save(cliente);
        assertEquals("CLIENTE ACME", cliente.getNombre());
        assertEquals("0991000000", cliente.getTelefono());
        assertEquals("ventas@acme.com.py", cliente.getEmail()); // lo que faltaba se completa
        ArgumentCaptor<Proveedor> proveedor = ArgumentCaptor.forClass(Proveedor.class);
        verify(proveedorService).save(proveedor.capture());
        assertSame(cliente, proveedor.getValue().getPersona());
    }

    @Test
    void conPersonaExistenteMuestraSusDatosYNoLosDeLaFactura() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Map<String, Object> fila = new HashMap<>();
        fila.put("id", 33L);
        fila.put("nombre", "CLIENTE ACME");
        fila.put("telefono", "0991000000");
        fila.put("direccion", "  ");
        when(jdbc.queryForList(contains("personas.persona"), eq(RUC))).thenReturn(List.of(fila));

        ProveedorDeFacturaMobile d = service.datos(null, 10L);

        assertEquals(33L, d.getPersonaId());
        assertEquals("0991000000", d.getTelefono()); // el de la persona, que no se pisa
        assertEquals("AVDA. ESPAÑA 1234", d.getDireccion()); // le falta: queda el de la factura
        assertEquals("ventas@acme.com.py", d.getEmail());
        assertEquals(List.of(
                new DatoDePersonaMobile("nombre", "CLIENTE ACME", "DISTRIBUIDORA ACME S.A."),
                new DatoDePersonaMobile("telefono", "0991000000", "021555666")), d.getDatosDePersona());
    }

    @Test
    void elMismoDatoEscritoDistintoNoSeOfreceActualizar() {
        assertTrue(ProveedorDesdeFacturaService.mismoDato("telefono", "0981 123-456", "+595981123456"));
        assertTrue(ProveedorDesdeFacturaService.mismoDato("direccion", "avda.  españa 1234", "AVDA. ESPAÑA 1234"));
        assertFalse(ProveedorDesdeFacturaService.mismoDato("telefono", "0991000000", "021555666"));
    }

    @Test
    void registrarActualizaDeLaPersonaSoloLoQueSePidio() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Map<String, Object> fila = new HashMap<>();
        fila.put("id", 33L);
        fila.put("nombre", "CLIENTE ACME");
        fila.put("telefono", "0991000000");
        fila.put("email", "viejo@acme.com");
        when(jdbc.queryForList(contains("personas.persona"), eq(RUC))).thenReturn(List.of(fila));
        Persona cliente = new Persona();
        cliente.setId(33L);
        cliente.setDocumento(RUC);
        cliente.setNombre("CLIENTE ACME");
        cliente.setTelefono("0991000000");
        cliente.setEmail("viejo@acme.com");
        when(personaService.findById(33L)).thenReturn(Optional.of(cliente));
        RegistrarProveedorFacturaInput in = input(10L);
        in.setNombre("DISTRIBUIDORA ACME S.A.");
        in.setTelefono("021555666");
        in.setEmail("ventas@acme.com.py");
        in.setActualizarPersona(List.of("telefono", "nombre"));

        service.registrar(in);

        assertEquals("021555666", cliente.getTelefono());
        assertEquals("DISTRIBUIDORA ACME S.A.", cliente.getNombre());
        assertEquals("viejo@acme.com", cliente.getEmail()); // no se pidió: queda el suyo
    }

    @Test
    void actualizarUnDatoQueNoExisteSeRechaza() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Map<String, Object> fila = new HashMap<>();
        fila.put("id", 33L);
        fila.put("nombre", "CLIENTE ACME");
        when(jdbc.queryForList(contains("personas.persona"), eq(RUC))).thenReturn(List.of(fila));
        Persona cliente = new Persona();
        cliente.setId(33L);
        cliente.setNombre("CLIENTE ACME");
        when(personaService.findById(33L)).thenReturn(Optional.of(cliente));
        RegistrarProveedorFacturaInput in = input(10L);
        in.setActualizarPersona(List.of("documento"));

        assertThrows(GraphQLException.class, () -> service.registrar(in));
        verify(personaService, never()).save(any());
    }

    @Test
    void registrarDosVecesDevuelveElMismoProveedor() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Proveedor existente = new Proveedor();
        existente.setId(7L);
        when(compras.proveedorPorRuc(RUC)).thenReturn(existente);

        ProveedorDeFacturaMobile r = service.registrar(input(10L));

        assertEquals(7L, r.getProveedorId());
        verify(personaService, never()).save(any());
        verify(proveedorService, never()).save(any());
    }

    @Test
    void registrarEncuentraElCreadoPorOtroMientrasEsperabaElLock() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        Proveedor otro = new Proveedor();
        otro.setId(8L);
        // Antes del lock no existe; después del lock, sí (lo creó el otro teléfono)
        when(compras.proveedorPorRuc(RUC)).thenReturn(null, otro, otro);
        when(proveedorService.findById(8L)).thenReturn(Optional.of(otro));

        ProveedorDeFacturaMobile r = service.registrar(input(10L));

        assertEquals(8L, r.getProveedorId());
        verify(proveedorService, never()).save(any());
    }

    @Test
    void papelTomaDireccionTelefonoYEmailDeLaIa() {
        String ia = "[{\"emisorDireccion\":null,\"emisorTelefono\":\"021 444\"},"
                + "{\"emisorDireccion\":\"MCAL. LOPEZ 100\",\"emisorEmail\":\"a@b.py\"}]";
        when(documentos.findById(11L)).thenReturn(Optional.of(papel(DV, ia)));

        ProveedorDeFacturaMobile d = service.datos(null, 11L);

        assertEquals("FOTO", d.getOrigen());
        assertTrue(d.getRucValido());
        assertEquals("ACME PAPEL", d.getNombre());
        assertEquals("MCAL. LOPEZ 100", d.getDireccion());
        assertEquals("021 444", d.getTelefono());
        assertEquals("a@b.py", d.getEmail());
        assertFalse(d.getCredito());
        assertTrue(d.getPuedeRegistrar());
    }

    @Test
    void papelConDvInvalidoNoSePuedeRegistrar() {
        when(documentos.findById(11L)).thenReturn(Optional.of(papel(DV_MAL, null)));

        ProveedorDeFacturaMobile d = service.datos(null, 11L);
        assertFalse(d.getRucValido());
        assertFalse(d.getPuedeRegistrar());
        assertTrue(d.getMotivo().contains("dígito verificador"));

        assertThrows(GraphQLException.class, () -> service.registrar(input(11L)));
        verify(proveedorService, never()).save(any());
    }

    @Test
    void papelSinPedidoQuedaConElProveedorNuevo() {
        DocumentoRecibido doc = papel(DV, null);
        when(documentos.findById(11L)).thenReturn(Optional.of(doc));
        when(proveedorService.findById(900L)).thenAnswer(inv -> {
            Proveedor p = new Proveedor();
            p.setId(900L);
            return Optional.of(p);
        });

        service.registrar(input(11L));

        assertNotNull(doc.getProveedor());
        assertEquals(900L, doc.getProveedor().getId());
        verify(documentos).save(doc);
    }

    @Test
    void sinRazonSocialNoSeRegistra() {
        DocumentoRecibido doc = papel(DV, null);
        doc.setRazonSocialEmisor(null);
        when(documentos.findById(11L)).thenReturn(Optional.of(doc));

        assertThrows(GraphQLException.class, () -> service.registrar(input(11L)));
        verify(personaService, never()).save(any());
    }

    @Test
    void chequeDiasFueraDeRangoSeRechaza() {
        when(documentos.findById(10L)).thenReturn(Optional.of(sifen()));
        RegistrarProveedorFacturaInput in = input(10L);
        in.setChequeDias(0);

        assertThrows(GraphQLException.class, () -> service.registrar(in));
        verify(proveedorService, never()).save(any());
    }

    @Test
    void completarDeIaIgnoraJsonRoto() {
        ProveedorDeFacturaMobile d = new ProveedorDeFacturaMobile();
        ProveedorDesdeFacturaService.completarDeIa(d, "{no es json");
        assertNull(d.getDireccion());
    }
}
