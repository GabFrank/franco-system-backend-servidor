package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.graphql.operaciones.input.TransferenciaItemInput;
import com.franco.dev.service.operaciones.ControlStockNegativoService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemLoteService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El control de stock negativo se consulta solo al cargar un item NUEVO, y un fallo al registrar
 * nunca impide guardar el item.
 */
class TransferenciaItemGraphQLControlStockTest {

    private static final Long ITEM_EXISTENTE = 65830L;

    private TransferenciaItemService service;
    private TransferenciaService transferenciaService;
    private ControlStockNegativoService controlStockNegativoService;
    private TransferenciaItemGraphQL resolver;

    @BeforeEach
    void setUp() {
        service = mock(TransferenciaItemService.class);
        controlStockNegativoService = mock(ControlStockNegativoService.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        transferenciaService = mock(TransferenciaService.class);
        PresentacionService presentacionService = mock(PresentacionService.class);

        resolver = new TransferenciaItemGraphQL();
        ReflectionTestUtils.setField(resolver, "service", service);
        ReflectionTestUtils.setField(resolver, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(resolver, "transferenciaService", transferenciaService);
        ReflectionTestUtils.setField(resolver, "transferenciaItemLoteService", mock(TransferenciaItemLoteService.class));
        ReflectionTestUtils.setField(resolver, "presentacionService", presentacionService);
        ReflectionTestUtils.setField(resolver, "movimientoStockService", mock(MovimientoStockService.class));
        ReflectionTestUtils.setField(resolver, "controlStockNegativoService", controlStockNegativoService);

        when(usuarioService.findById(55L)).thenReturn(Optional.of(new Usuario()));
        when(transferenciaService.findById(any())).thenReturn(Optional.of(new Transferencia()));
        when(presentacionService.findById(any())).thenAnswer(i -> Optional.of(new Presentacion()));
        when(service.save(any())).thenAnswer(i -> i.getArgument(0));
        TransferenciaItem existente = new TransferenciaItem();
        existente.setId(ITEM_EXISTENTE);
        when(service.findById(ITEM_EXISTENTE)).thenReturn(Optional.of(existente));
    }

    private TransferenciaItemInput input(Long id) {
        TransferenciaItemInput in = new TransferenciaItemInput();
        in.setId(id);
        in.setTransferenciaId(6290L);
        in.setPresentacionPreTransferenciaId(13261L);
        in.setCantidadPreTransferencia(3D);
        in.setUsuarioId(55L);
        return in;
    }

    @Test
    void unItemNuevoConsultaElControl() {
        resolver.saveTransferenciaItem(input(null), null);
        verify(controlStockNegativoService, times(1)).registrarTransferencia(any());
    }

    /** COMPRAS tiene stock negativo por diseno: la mercaderia "nace" ahi al cargar la compra. */
    @Test
    void unItemQueSaleDeComprasNoSeRegistra() {
        com.franco.dev.domain.empresarial.Sucursal compras = new com.franco.dev.domain.empresarial.Sucursal();
        compras.setNombre(com.franco.dev.service.productos.CostosPorProductoService.SUCURSAL_COMPRAS);
        Transferencia desdeCompras = new Transferencia();
        desdeCompras.setSucursalOrigen(compras);
        when(transferenciaService.findById(any())).thenReturn(Optional.of(desdeCompras));

        resolver.saveTransferenciaItem(input(null), null);

        verify(controlStockNegativoService, never()).registrarTransferencia(any());
    }

    @Test
    void editarUnItemExistenteNoVuelveARegistrar() {
        resolver.saveTransferenciaItem(input(ITEM_EXISTENTE), null);
        verify(controlStockNegativoService, never()).registrarTransferencia(any());
    }

    @Test
    void unFalloAlRegistrarNoImpideGuardarElItem() {
        when(controlStockNegativoService.registrarTransferencia(any()))
                .thenThrow(new RuntimeException("base caida"));

        TransferenciaItem guardado = resolver.saveTransferenciaItem(input(null), null);

        assertNotNull(guardado);
        verify(service).save(any());
    }
}
