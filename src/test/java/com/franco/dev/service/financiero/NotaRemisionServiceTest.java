package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.NotaRemision;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.NotaRemisionItem;
import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.domain.financiero.TimbradoDetalle;
import com.franco.dev.domain.financiero.enums.MotivoEmisionNotaRemision;
import com.franco.dev.domain.financiero.enums.OrigenNotaRemision;
import com.franco.dev.domain.financiero.enums.ResponsableEmisionNr;
import com.franco.dev.repository.financiero.NotaRemisionItemRepository;
import com.franco.dev.repository.financiero.NotaRemisionRepository;
import com.franco.dev.repository.financiero.TimbradoDetalleRepository;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import com.franco.dev.service.sifen.util.SerieDeNumeracionValidator;

import static org.mockito.Mockito.*;

class NotaRemisionServiceTest {

    private static final Long TIMBRADO = 10L;
    private static final Long SUCURSAL = 1L;
    private static final Long TRANSFERENCIA = 51338L;
    private static final String RUC_EMPRESA = "80099999-1";

    private NotaRemisionRepository repository;
    private NotaRemisionItemRepository itemRepository;
    private TimbradoDetalleRepository timbradoDetalleRepository;
    private FacturacionSecurityService seg;
    private SerieDeNumeracionValidator serieValidator;
    private TransferenciaService transferenciaService;
    private TimbradoDetalle timbrado;
    private NotaRemisionService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotaRemisionRepository.class);
        itemRepository = mock(NotaRemisionItemRepository.class);
        timbradoDetalleRepository = mock(TimbradoDetalleRepository.class);
        seg = mock(FacturacionSecurityService.class);
        serieValidator = mock(SerieDeNumeracionValidator.class);
        transferenciaService = mock(TransferenciaService.class);
        service = new NotaRemisionService(repository, itemRepository, timbradoDetalleRepository,
                seg, serieValidator, transferenciaService);

        Timbrado cabecera = new Timbrado();
        cabecera.setRuc(RUC_EMPRESA);
        timbrado = new TimbradoDetalle();
        timbrado.setId(TIMBRADO);
        timbrado.setSucursalId(SUCURSAL);
        timbrado.setTimbrado(cabecera);
        timbrado.setActivo(true);
        when(timbradoDetalleRepository.lockById(TIMBRADO)).thenReturn(Optional.of(timbrado));
        when(repository.save(any(NotaRemision.class))).thenAnswer(i -> {
            NotaRemision n = i.getArgument(0);
            if (n.getId() == null) n.setId(500L);
            return n;
        });
        when(itemRepository.save(any(NotaRemisionItem.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findActivasByTransferenciaId(any())).thenReturn(Collections.emptyList());
        // Con @IdClass no se puede usar @GeneratedValue: el id sale de la secuencia.
        when(repository.siguienteId()).thenReturn(500L);
        when(itemRepository.siguienteId()).thenReturn(900L);
    }

    @Test
    void laPrimeraNotaDelTimbradoEsLaNumeroUno() {
        when(repository.findMaxNumeroByTimbradoDetalleId(TIMBRADO)).thenReturn(0);

        NotaRemision guardada = service.crear(notaManual(), items());

        assertEquals(1, guardada.getNumeroNotaRemision());
        verify(seg).requireEmitirRemision(OrigenNotaRemision.MANUAL);
        verifyNoInteractions(transferenciaService);
        verify(timbradoDetalleRepository).lockById(TIMBRADO);
    }

    @Test
    void laSiguienteSigueElMaximoDeLaSerie() {
        when(repository.findMaxNumeroByTimbradoDetalleId(TIMBRADO)).thenReturn(417);

        assertEquals(418, service.crear(notaManual(), items()).getNumeroNotaRemision());
    }

    @Test
    void elNumeroSeTomaConElTimbradoBloqueado() {
        when(repository.findMaxNumeroByTimbradoDetalleId(TIMBRADO)).thenReturn(3);

        service.crear(notaManual(), items());

        org.mockito.InOrder orden = inOrder(timbradoDetalleRepository, repository);
        orden.verify(timbradoDetalleRepository).lockById(TIMBRADO);
        orden.verify(repository).findMaxNumeroByTimbradoDetalleId(TIMBRADO);
    }

    @Test
    void losItemsHeredanLaNotaYLaSucursal() {
        when(repository.findMaxNumeroByTimbradoDetalleId(TIMBRADO)).thenReturn(0);
        List<NotaRemisionItem> items = items();

        service.crear(notaManual(), items);

        assertEquals(500L, items.get(0).getNotaRemisionId());
        assertEquals(900L, items.get(0).getId(), "el ítem también toma su id de la secuencia");
        assertEquals(SUCURSAL, items.get(0).getSucursalId());
    }

    @Test
    void sinItemsNoSeEmite() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.crear(notaManual(), Collections.emptyList()));
        assertTrue(e.getMessage().contains("al menos un ítem"));
        verify(repository, never()).save(any());
    }

    @Test
    void elReceptorNoPuedeSerInnominado() {
        NotaRemision sinRuc = notaManual();
        sinRuc.setReceptorRuc("  ");

        assertThrows(GraphQLException.class, () -> service.crear(sinRuc, items()));
    }

    @Test
    void unaCantidadNoPositivaSeRechaza() {
        NotaRemisionItem item = items().get(0);
        item.setCantidad(BigDecimal.ZERO);

        assertThrows(GraphQLException.class,
                () -> service.crear(notaManual(), Collections.singletonList(item)));
    }

    @Test
    void laTransferenciaNoPuedeTenerDosNotasActivas() {
        NotaRemision desdeTransferencia = notaManual();
        desdeTransferencia.setOrigen(OrigenNotaRemision.TRANSFERENCIA);
        desdeTransferencia.setTransferenciaId(77L);
        when(repository.findActivasByTransferenciaId(77L))
                .thenReturn(Collections.singletonList(new NotaRemision()));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.crear(desdeTransferencia, items()));
        assertTrue(e.getMessage().contains("ya tiene una nota de remisión activa"));
    }

    @Test
    void laFacturaTampocoPuedeTenerDosNotasActivas() {
        // El origen FACTURA no tenía guard: dos clicks en «Guardar» creaban dos notas y quemaban
        // dos números de la serie. Lo destapó la auditoría del plan de ajustes operativos.
        NotaRemision desdeFactura = notaManual();
        desdeFactura.setOrigen(OrigenNotaRemision.FACTURA);
        desdeFactura.setFacturaLegalId(555L);
        when(repository.findActivasByFacturaLegalId(555L, desdeFactura.getSucursalId()))
                .thenReturn(Collections.singletonList(new NotaRemision()));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.crear(desdeFactura, items()));
        assertTrue(e.getMessage().contains("ya tiene una nota de remisión activa"));
    }

    @Test
    void elOrigenTransferenciaExigeLaTransferencia() {
        NotaRemision sinReferencia = notaManual();
        sinReferencia.setOrigen(OrigenNotaRemision.TRANSFERENCIA);

        assertThrows(GraphQLException.class, () -> service.crear(sinReferencia, items()));
    }

    @Test
    void elTimbradoInactivoNoEmite() {
        TimbradoDetalle inactivo = new TimbradoDetalle();
        inactivo.setId(TIMBRADO);
        inactivo.setActivo(false);
        when(timbradoDetalleRepository.lockById(TIMBRADO)).thenReturn(Optional.of(inactivo));

        assertThrows(GraphQLException.class, () -> service.crear(notaManual(), items()));
    }

    @Test
    void anularFueraDeLaVentanaDeSifenFalla() {
        NotaRemision vieja = notaManual();
        vieja.setId(1L);
        vieja.setActivo(true);
        vieja.setFecha(LocalDateTime.now().minusHours(NotaRemisionService.HORAS_PARA_ANULAR + 1));
        when(repository.findByIdAndSucursalId(1L, SUCURSAL)).thenReturn(Optional.of(vieja));

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.anular(1L, SUCURSAL));
        assertTrue(e.getMessage().contains("168"));
        assertTrue(vieja.getActivo(), "la nota no se apaga si SIFEN ya no acepta la cancelación");
    }

    @Test
    void anularDentroDeLaVentanaApagaLaNota() {
        NotaRemision reciente = notaManual();
        reciente.setId(2L);
        reciente.setActivo(true);
        reciente.setFecha(LocalDateTime.now().minusHours(2));
        when(repository.findByIdAndSucursalId(2L, SUCURSAL)).thenReturn(Optional.of(reciente));

        service.anular(2L, SUCURSAL);

        assertFalse(reciente.getActivo());
        verify(seg).requireEmitir();
    }

    // ---- Rol NOTA REMISION EMITIR: solo la nota de una transferencia real de la sucursal ----

    @Test
    void elRolAcotadoEmiteLaNotaDeSuTransferenciaYQuedaComoEmisor() {
        actorAcotado();
        transferenciaDesde(SUCURSAL);
        NotaRemision nota = notaDeTransferencia();
        nota.setUsuarioId(999L);

        NotaRemision guardada = service.crear(nota, items());

        assertEquals(1, guardada.getNumeroNotaRemision());
        assertEquals(7L, guardada.getUsuarioId(), "el emisor es el usuario autenticado, no el del input");
    }

    @Test
    void elRolAcotadoNoEmiteSiElServicioDeRolesRechazaElOrigen() {
        doThrow(new GraphQLException("No autorizado")).when(seg).requireEmitirRemision(OrigenNotaRemision.MANUAL);

        assertThrows(GraphQLException.class, () -> service.crear(notaManual(), items()));
        verify(timbradoDetalleRepository, never()).lockById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void elRolAcotadoNoEmiteParaUnaTransferenciaQueNoExiste() {
        actorAcotado();
        when(transferenciaService.findById(TRANSFERENCIA)).thenReturn(Optional.empty());

        rechazaSinNumerar(notaDeTransferencia(), "No existe la transferencia");
    }

    @Test
    void elRolAcotadoNoEmiteParaUnaTransferenciaDeOtraSucursal() {
        actorAcotado();
        transferenciaDesde(13L);

        rechazaSinNumerar(notaDeTransferencia(), "otra sucursal");
    }

    @Test
    void elRolAcotadoNoEmiteConOtroMotivo() {
        actorAcotado();
        transferenciaDesde(SUCURSAL);
        NotaRemision nota = notaDeTransferencia();
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_POR_CONSIGNACION);

        rechazaSinNumerar(nota, "No autorizado");
    }

    @Test
    void elRolAcotadoNoEmiteANombreDeUnTercero() {
        actorAcotado();
        transferenciaDesde(SUCURSAL);
        NotaRemision nota = notaDeTransferencia();
        nota.setReceptorRuc("80012345-6");

        rechazaSinNumerar(nota, "propia empresa");
    }

    @Test
    void elRolAcotadoNoEmiteConElTimbradoDeOtraSucursal() {
        actorAcotado();
        transferenciaDesde(SUCURSAL);
        timbrado.setSucursalId(13L);

        rechazaSinNumerar(notaDeTransferencia(), "timbrado");
    }

    @Test
    void conFacturacionEmitirLaNotaDeTransferenciaNoPasaPorLasValidacionesDelRolAcotado() {
        when(seg.emiteSoloDesdeTransferencia()).thenReturn(false);
        NotaRemision nota = notaDeTransferencia();
        nota.setReceptorRuc("80012345-6");
        nota.setUsuarioId(999L);

        NotaRemision guardada = service.crear(nota, items());

        assertEquals(999L, guardada.getUsuarioId());
        verifyNoInteractions(transferenciaService);
    }

    private void actorAcotado() {
        when(seg.emiteSoloDesdeTransferencia()).thenReturn(true);
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(seg.currentUsuario()).thenReturn(usuario);
    }

    private void transferenciaDesde(Long sucursalOrigenId) {
        Sucursal origen = new Sucursal();
        origen.setId(sucursalOrigenId);
        Transferencia transferencia = new Transferencia();
        transferencia.setId(TRANSFERENCIA);
        transferencia.setSucursalOrigen(origen);
        when(transferenciaService.findById(TRANSFERENCIA)).thenReturn(Optional.of(transferencia));
    }

    private void rechazaSinNumerar(NotaRemision nota, String fragmento) {
        GraphQLException e = assertThrows(GraphQLException.class, () -> service.crear(nota, items()));
        assertTrue(e.getMessage().contains(fragmento), e.getMessage());
        verify(repository, never()).findMaxNumeroByTimbradoDetalleId(any());
        verify(repository, never()).save(any());
    }

    private static NotaRemision notaDeTransferencia() {
        NotaRemision nota = notaManual();
        nota.setOrigen(OrigenNotaRemision.TRANSFERENCIA);
        nota.setTransferenciaId(TRANSFERENCIA);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_ENTRE_LOCALES);
        nota.setReceptorRuc(RUC_EMPRESA);
        return nota;
    }

    private static NotaRemision notaManual() {
        NotaRemision nota = new NotaRemision();
        nota.setSucursalId(SUCURSAL);
        nota.setTimbradoDetalleId(TIMBRADO);
        nota.setOrigen(OrigenNotaRemision.MANUAL);
        nota.setMotivoEmision(MotivoEmisionNotaRemision.TRASLADO_POR_CONSIGNACION);
        nota.setResponsableEmision(ResponsableEmisionNr.EMISOR_FACTURA);
        nota.setReceptorNombre("CLIENTE DE PRUEBA");
        nota.setReceptorRuc("80012345-6");
        return nota;
    }

    private static List<NotaRemisionItem> items() {
        NotaRemisionItem item = new NotaRemisionItem();
        item.setDescripcion("PRODUCTO DE PRUEBA");
        item.setCantidad(new BigDecimal("3"));
        return new java.util.ArrayList<>(Arrays.asList(item));
    }

    @Test
    void notasDeVariasTransferenciasSaleEnUnaSolaConsulta() {
        NotaRemision nota = new NotaRemision();
        nota.setId(11L);
        nota.setTransferenciaId(51338L);
        List<Long> ids = Arrays.asList(51338L, 51322L);
        when(repository.findActivasByTransferenciaIdIn(ids)).thenReturn(Collections.singletonList(nota));

        assertEquals(Collections.singletonList(nota), service.findActivasByTransferencias(ids));
        verify(repository, times(1)).findActivasByTransferenciaIdIn(ids);
    }

    @Test
    void sinTransferenciasNoConsulta() {
        assertTrue(service.findActivasByTransferencias(Collections.emptyList()).isEmpty());
        assertTrue(service.findActivasByTransferencias(null).isEmpty());
        verify(repository, never()).findActivasByTransferenciaIdIn(any());
    }

    @Test
    void masDeDoscientasTransferenciasSeRechazan() {
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 0; i <= NotaRemisionService.MAX_TRANSFERENCIAS_POR_CONSULTA; i++) ids.add(i);

        assertThrows(GraphQLException.class, () -> service.findActivasByTransferencias(ids));
        verify(repository, never()).findActivasByTransferenciaIdIn(any());
    }
}
