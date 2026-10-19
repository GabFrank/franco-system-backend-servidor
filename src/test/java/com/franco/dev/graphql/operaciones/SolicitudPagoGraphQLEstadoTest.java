package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.enums.SolicitudPagoEstado;
import com.franco.dev.domain.operaciones.enums.TipoSolicitudPago;
import com.franco.dev.graphql.operaciones.input.SolicitudPagoDetalleInput;
import com.franco.dev.repository.operaciones.SolicitudPagoDetalleRepository;
import com.franco.dev.repository.operaciones.SolicitudPagoRepository;
import com.franco.dev.service.operaciones.SolicitudPagoDetalleService;
import com.franco.dev.service.operaciones.SolicitudPagoNotaRecepcionService;
import com.franco.dev.service.operaciones.SolicitudPagoService;
import graphql.GraphqlErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * #435: las mutations sueltas del diálogo de compras validan el estado de la solicitud antes de tocarla. Notas solo
 * y formas de pago solo en PENDIENTE o DEVUELTO. Un rechazo llega con su motivo y no cambia nada.
 */
class SolicitudPagoGraphQLEstadoTest {

    private static final long ID = 1L;
    private static final long DETALLE = 5L;

    @Mock private SolicitudPagoService solicitudPagoService;
    @Mock private SolicitudPagoNotaRecepcionService notaService;
    @Mock private SolicitudPagoDetalleService detalleService;
    @Mock private SolicitudPagoRepository solicitudRepo;
    @Mock private SolicitudPagoDetalleRepository detalleRepo;

    @InjectMocks private SolicitudPagoGraphQL resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(solicitudPagoService.getRepository()).thenReturn(solicitudRepo);
        when(detalleService.getRepository()).thenReturn(detalleRepo);
        when(solicitudRepo.findTipoById(ID)).thenReturn(Optional.of(TipoSolicitudPago.COMPRA));
        when(detalleRepo.findSolicitudIdById(DETALLE)).thenReturn(Optional.of(ID));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void bloqueada() {
        when(solicitudPagoService.exigirEditable(eq(ID), any(), anyString()))
                .thenThrow(new IllegalStateException("No se puede X: la solicitud SP-1 está en CONCLUIDO."));
    }

    private void editable() {
        when(solicitudPagoService.exigirEditable(eq(ID), any(), anyString())).thenReturn(new SolicitudPago());
    }

    private void rechazaConMotivo(org.junit.jupiter.api.function.Executable llamada) {
        GraphqlErrorException e = assertThrows(GraphqlErrorException.class, llamada);
        assertEquals("No se puede X: la solicitud SP-1 está en CONCLUIDO.", e.getMessage());
    }

    @Test
    void quitarNotaDeUnaBloqueadaNoQuitaNada() {
        bloqueada();
        rechazaConMotivo(() -> resolver.removerNotaDeSolicitudPago(ID, 9L));
        verify(notaService, never()).removerNotaDeSolicitud(anyLong(), anyLong());
    }

    @Test
    void borrarFormaDePagoDeUnaBloqueadaNoBorraNada() {
        bloqueada();
        rechazaConMotivo(() -> resolver.eliminarSolicitudPagoDetalle(DETALLE));
        verify(detalleService, never()).eliminarDetalle(anyLong());
    }

    @Test
    void agregarFormaDePagoAUnaBloqueadaNoAgregaNada() {
        bloqueada();
        rechazaConMotivo(() -> resolver.agregarSolicitudPagoDetalle(ID, new SolicitudPagoDetalleInput()));
        verify(detalleService, never()).agregarDetalle(any(), any());
    }

    @Test
    void notasYFormasDePagoPidenLosMismosEstadosEditables() {
        editable();
        when(detalleService.eliminarDetalle(DETALLE)).thenReturn(true);

        resolver.removerNotaDeSolicitudPago(ID, 9L);
        resolver.eliminarSolicitudPagoDetalle(DETALLE);
        resolver.agregarSolicitudPagoDetalle(ID, new SolicitudPagoDetalleInput());

        verify(solicitudPagoService).exigirEditable(ID, SolicitudPagoService.ESTADOS_EDITABLES, "quitar notas");
        verify(solicitudPagoService, times(2)).exigirEditable(ID, SolicitudPagoService.ESTADOS_EDITABLES,
                "cambiar las formas de pago");
        Set<SolicitudPagoEstado> editables = SolicitudPagoService.ESTADOS_EDITABLES;
        assertFalse(editables.contains(SolicitudPagoEstado.PARCIAL));
    }

    @Test
    void borrarUnaFormaDePagoQueYaNoExisteDevuelveTrue() {
        when(detalleRepo.findSolicitudIdById(DETALLE)).thenReturn(Optional.empty());
        when(detalleService.eliminarDetalle(DETALLE)).thenReturn(true);

        assertTrue(resolver.eliminarSolicitudPagoDetalle(DETALLE));
        verify(solicitudPagoService, never()).exigirEditable(anyLong(), any(), anyString());
    }

    /**
     * La edición no puede cargar la solicitud antes del lock: lockById devolvería la instancia ya cargada sin
     * refrescar y validaría un estado viejo (SolicitudPagoRepository#findTipoById explica el mismo caso).
     */
    @Test
    void laEdicionNoCargaLaSolicitudAntesDelLock() {
        SolicitudPago bloqueada = new SolicitudPago();
        bloqueada.setId(ID);
        java.util.concurrent.atomic.AtomicBoolean conLock = new java.util.concurrent.atomic.AtomicBoolean();
        when(solicitudPagoService.exigirEditable(eq(ID), any(), anyString())).thenAnswer(i -> {
            conLock.set(true);
            return bloqueada;
        });
        // Un Error, no una Exception: el catch del resolver no lo tapa.
        when(solicitudPagoService.findById(ID)).thenAnswer(i -> {
            if (!conLock.get()) throw new AssertionError("findById antes del lock");
            return Optional.of(bloqueada);
        });
        SolicitudPagoGraphQL.SolicitudPagoInput in = new SolicitudPagoGraphQL.SolicitudPagoInput();
        in.setId(ID);

        try {
            resolver.actualizarSolicitudPago(in);
        } catch (RuntimeException ignorada) {
            // Lo que pase después (moneda, detalles) no importa acá: solo el orden del lock.
        }

        verify(solicitudPagoService).exigirEditable(eq(ID), any(), anyString());
    }

    /** El monto lo calcula el servidor: el que manda el cliente se ignora. */
    @Test
    void agregarNotaNoUsaElMontoDelCliente() {
        resolver.agregarNotaASolicitudPago(ID, 9L, 123.45);
        verify(solicitudPagoService).agregarNota(ID, 9L);
        verify(notaService, never()).agregarNotaASolicitud(anyLong(), anyLong(), anyDouble());
    }
}
