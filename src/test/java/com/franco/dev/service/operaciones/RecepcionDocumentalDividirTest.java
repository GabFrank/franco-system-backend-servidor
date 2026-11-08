package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.graphql.operaciones.input.DividirPedidoItemInput;
import com.franco.dev.graphql.operaciones.input.ParteDivisionPedidoItemInput;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * «Dividir Ítem» con «Crear Nueva Nota» no guardaba nada y el desktop avisaba éxito: el central salteaba en silencio
 * las partes con nota 0 (reporte de Alejandro, 2026-10-29, pedido 2085). Ahora la nota se carga antes en el desktop y
 * el central rechaza toda parte sin nota válida.
 */
class RecepcionDocumentalDividirTest {

    @Mock private NotaRecepcionItemService notaRecepcionItemService;
    @Mock private NotaRecepcionService notaRecepcionService;
    @Mock private PedidoItemService pedidoItemService;
    @Mock private PresentacionService presentacionService;
    @Mock private UsuarioService usuarioService;
    @Mock private NotaRecepcionItemDistribucionService notaRecepcionItemDistribucionService;
    @Mock private PedidoItemDistribucionService pedidoItemDistribucionService;
    @Mock private CambioService cambioService;
    @InjectMocks private RecepcionDocumentalService service;
    private AutoCloseable mocks;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        pedido = new Pedido();
        pedido.setId(2085L);
        PedidoItem pedidoItem = new PedidoItem();
        pedidoItem.setId(7L);
        pedidoItem.setPedido(pedido);
        pedidoItem.setCantidadSolicitada(34.0);
        when(pedidoItemService.findById(7L)).thenReturn(Optional.of(pedidoItem));
        when(notaRecepcionItemService.save(any(NotaRecepcionItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private DividirPedidoItemInput division(ParteDivisionPedidoItemInput... partes) {
        DividirPedidoItemInput input = new DividirPedidoItemInput();
        input.setPedidoItemId(7L);
        input.setPartes(Arrays.asList(partes));
        return input;
    }

    private ParteDivisionPedidoItemInput parte(Long notaId, Double cantidad) {
        ParteDivisionPedidoItemInput parte = new ParteDivisionPedidoItemInput();
        parte.setNotaRecepcionId(notaId);
        parte.setCantidad(cantidad);
        return parte;
    }

    private NotaRecepcion notaExistente(long id, Pedido de) {
        NotaRecepcion nota = new NotaRecepcion();
        nota.setId(id);
        nota.setPedido(de);
        when(notaRecepcionService.findById(id)).thenReturn(Optional.of(nota));
        return nota;
    }

    @Test
    void repartePorNotasCargadas() {
        NotaRecepcion primera = notaExistente(50L, pedido);
        NotaRecepcion segunda = notaExistente(51L, pedido);

        List<NotaRecepcionItem> items = service.dividir(division(parte(50L, 17.0), parte(51L, 17.0)));

        assertEquals(2, items.size());
        assertEquals(primera, items.get(0).getNotaRecepcion());
        assertEquals(segunda, items.get(1).getNotaRecepcion());
        assertEquals(17.0, items.get(0).getCantidadEnNota());
    }

    @Test
    void notaCeroYaNoCreaNotaSola() {
        notaExistente(50L, pedido);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.dividir(division(parte(50L, 17.0), parte(0L, 17.0))));
        assertTrue(e.getMessage().contains("parte 2"));
        verify(notaRecepcionService, never()).save(any());
    }

    @Test
    void parteSinCantidadRechazaTodo() {
        notaExistente(50L, pedido);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> service.dividir(division(parte(50L, 17.0), parte(50L, 0.0))));
        assertTrue(e.getMessage().contains("parte 2"));
    }

    @Test
    void parteSinNotaRechazaTodo() {
        assertThrows(GraphQLException.class, () -> service.dividir(division(parte(null, 17.0))));
        verify(notaRecepcionItemService, never()).save(any());
    }

    @Test
    void notaInexistenteRechazaTodo() {
        when(notaRecepcionService.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(GraphQLException.class, () -> service.dividir(division(parte(99L, 17.0))));
        verify(notaRecepcionItemService, never()).save(any());
    }

    @Test
    void notaDeOtroPedidoRechazaTodo() {
        Pedido otro = new Pedido();
        otro.setId(1L);
        notaExistente(60L, otro);
        assertThrows(GraphQLException.class, () -> service.dividir(division(parte(60L, 17.0))));
        verify(notaRecepcionItemService, never()).save(any());
    }

    @Test
    void sinPartesEsError() {
        assertThrows(GraphQLException.class, () -> service.dividir(division()));
    }
}
