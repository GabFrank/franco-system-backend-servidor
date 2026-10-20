package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * #463: el movimiento de una transferencia con lote toma el lock de la sucursal origen ANTES de tocar
 * ningún movimiento. Si lo pidiera recién al repartir por lote, esperaría con la fila de la salida
 * ya tomada y podría cruzarse con un avance de la misma transferencia.
 */
class MovimientoStockServiceBloqueoLoteTest {

    private static final long ORIGEN = 13L;
    private static final long DESTINO = 20L;

    private MovimientoStockRepository repository;
    private LoteFefoService loteFefo;
    private MovimientoStockService service;

    @BeforeEach
    void setUp() {
        repository = mock(MovimientoStockRepository.class);
        loteFefo = mock(LoteFefoService.class);
        // @AllArgsConstructor (orden de declaración): repository, modificacionService, sucursalService,
        // movimientoStockLoteService, loteFefoService, transferenciaItemLoteService.
        service = new MovimientoStockService(
                repository,
                mock(ModificacionService.class),
                mock(SucursalService.class),
                mock(MovimientoStockLoteService.class),
                loteFefo,
                mock(TransferenciaItemLoteService.class));
    }

    private TransferenciaItem item(boolean conLote) {
        Producto producto = new Producto();
        producto.setId(4680L);
        producto.setLote(conLote);
        Presentacion presentacion = new Presentacion();
        presentacion.setProducto(producto);
        Sucursal origen = new Sucursal();
        origen.setId(ORIGEN);
        Sucursal destino = new Sucursal();
        destino.setId(DESTINO);
        Transferencia transferencia = new Transferencia();
        transferencia.setSucursalOrigen(origen);
        transferencia.setSucursalDestino(destino);
        // Una etapa sin case: el método no escribe nada y se ve solo el lock.
        transferencia.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);
        TransferenciaItem item = new TransferenciaItem();
        item.setId(352751L);
        item.setTransferencia(transferencia);
        item.setPresentacionPreTransferencia(presentacion);
        return item;
    }

    @Test
    void conLoteTomaElLockDelOrigenAntesDeTocarMovimientos() {
        // Si el lock no se consigue, no se llegó a buscar ni a guardar ningún movimiento.
        IllegalStateException sinLock = new IllegalStateException("sin lock");
        doThrow(sinLock).when(loteFefo).bloquearSaldo(ORIGEN);

        assertSame(sinLock, assertThrows(IllegalStateException.class,
                () -> service.createMovimientoFromTransferenciaItem(item(true))));

        verifyNoInteractions(repository);
    }

    @Test
    void sinLoteNoTomaElLock() {
        service.createMovimientoFromTransferenciaItem(item(false));

        verify(loteFefo, never()).bloquearSaldo(anyLong());
    }
}
