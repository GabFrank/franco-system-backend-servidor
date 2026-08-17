package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * saveTransferenciaItem persiste con merge, asi que toda columna ausente en el input se guardaba
 * como null. Es lo que borro las tres etapas y el creado_en del item 65830 de la transferencia
 * 6290, dejandolo en "Falta verificar" para siempre. El save tiene que ser un PATCH.
 */
class TransferenciaItemGraphQLPreservacionTest {

    private static final Long ITEM_ID = 65830L;
    private static final LocalDateTime CREADO = LocalDateTime.of(2026, 8, 14, 17, 34, 50);

    private TransferenciaItemService service;
    private TransferenciaItemGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaItemService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        TransferenciaService transferenciaService = mock(TransferenciaService.class);
        TransferenciaItemLoteService loteService = mock(TransferenciaItemLoteService.class);
        PresentacionService presentacionService = mock(PresentacionService.class);
        MovimientoStockService movimientoStockService = mock(MovimientoStockService.class);

        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "transferenciaService", transferenciaService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemLoteService", loteService);
        ReflectionTestUtils.setField(resolver, "presentacionService", presentacionService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", movimientoStockService);

        when(usuarioService.findById(55L)).thenReturn(Optional.of(new Usuario()));
        when(transferenciaService.findById(any())).thenReturn(Optional.of(new Transferencia()));
        when(presentacionService.findById(any())).thenAnswer(i -> Optional.of(new Presentacion()));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        when(service.findById(ITEM_ID)).thenReturn(Optional.of(itemYaRecepcionado()));
    }

    /** El item 65830 tal como estaba antes de que un save incompleto le borrara las etapas. */
    private TransferenciaItem itemYaRecepcionado() {
        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(ITEM_ID);
        ti.setCantidadPreTransferencia(2D);
        ti.setCantidadPreparacion(2D);
        ti.setCantidadTransporte(2D);
        ti.setCantidadRecepcion(2D);
        ti.setPresentacionPreparacion(new Presentacion());
        ti.setPresentacionTransporte(new Presentacion());
        ti.setPresentacionRecepcion(new Presentacion());
        ti.setCreadoEn(CREADO);
        ti.setVencimientoVerificado(true);
        return ti;
    }

    /** Lo que manda el desktop cuando solo edita la cantidad pedida: nada de las etapas siguientes. */
    private TransferenciaItemInput inputSoloPreTransferencia() {
        TransferenciaItemInput in = new TransferenciaItemInput();
        in.setId(ITEM_ID);
        in.setTransferenciaId(6290L);
        in.setPresentacionPreTransferenciaId(13261L);
        in.setCantidadPreTransferencia(3D);
        in.setUsuarioId(55L);
        return in;
    }

    @Test
    @DisplayName("Un input que solo trae pre-transferencia no borra las cantidades de las etapas siguientes")
    void noBorraLasCantidadesDeLasEtapas() {
        TransferenciaItem out = resolver.saveTransferenciaItem(inputSoloPreTransferencia(), null);

        assertEquals(3D, out.getCantidadPreTransferencia(), "la cantidad editada si debe cambiar");
        assertEquals(2D, out.getCantidadPreparacion(), "cantidad_preparacion no debia borrarse");
        assertEquals(2D, out.getCantidadTransporte(), "cantidad_transporte no debia borrarse");
        assertEquals(2D, out.getCantidadRecepcion(), "cantidad_recepcion no debia borrarse");
    }

    @Test
    @DisplayName("Un input que no trae presentaciones de etapa no las borra")
    void noBorraLasPresentacionesDeLasEtapas() {
        TransferenciaItem out = resolver.saveTransferenciaItem(inputSoloPreTransferencia(), null);

        assertNotNull(out.getPresentacionPreparacion(), "presentacion_preparacion no debia borrarse");
        assertNotNull(out.getPresentacionTransporte(), "presentacion_transporte no debia borrarse");
        assertNotNull(out.getPresentacionRecepcion(), "presentacion_recepcion no debia borrarse");
    }

    @Test
    @DisplayName("Un input sin creadoEn no borra la fecha de creacion")
    void noBorraCreadoEn() {
        TransferenciaItem out = resolver.saveTransferenciaItem(inputSoloPreTransferencia(), null);

        assertEquals(CREADO, out.getCreadoEn(), "creado_en no debia borrarse");
    }

    @Test
    @DisplayName("Un input sin vencimientoVerificado no lo vuelve a false")
    void noReseteaVencimientoVerificado() {
        TransferenciaItem out = resolver.saveTransferenciaItem(inputSoloPreTransferencia(), null);

        assertTrue(out.getVencimientoVerificado(), "vencimiento_verificado no debia resetearse");
    }

    @Test
    @DisplayName("Un input sin usuarioId falla con un error claro en vez de NullPointerException")
    void sinUsuarioIdErrorClaro() {
        TransferenciaItemInput in = inputSoloPreTransferencia();
        in.setUsuarioId(null);

        assertThrows(GraphQLException.class, () -> resolver.saveTransferenciaItem(in, null));

        verify(service, never()).save(any());
    }
}
