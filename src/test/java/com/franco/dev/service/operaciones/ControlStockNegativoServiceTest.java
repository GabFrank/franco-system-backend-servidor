package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.EntityManager;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Una transferencia se registra en el control solo si el stock del origen ya era 0 o negativo
 * antes de cargar el item. La cantidad se guarda en unidades, no en presentaciones. Registrar es
 * un control, no parte de la operacion: nunca lanza.
 */
class ControlStockNegativoServiceTest {

    private static final long PRODUCTO_ID = 77L;
    private static final long ORIGEN_ID = 6L;

    private ControlStockNegativoService service;

    @BeforeEach
    void setUp() {
        // spy: las dos salidas a la base (stockPrevio e insertarTransferencia) se reemplazan por test.
        service = spy(new ControlStockNegativoService(mock(JdbcTemplate.class), mock(EntityManager.class)));
        doNothing().when(service).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    /** Item de 3 cajas de 12 unidades, de la sucursal 6, transferencia 900, cargado por el usuario 55. */
    private TransferenciaItem item() {
        Producto producto = new Producto();
        producto.setId(PRODUCTO_ID);
        Presentacion presentacion = new Presentacion();
        presentacion.setProducto(producto);
        presentacion.setCantidad(12D);
        Sucursal origen = new Sucursal();
        origen.setId(ORIGEN_ID);
        Transferencia transferencia = new Transferencia();
        transferencia.setId(900L);
        transferencia.setSucursalOrigen(origen);
        Usuario usuario = new Usuario();
        usuario.setId(55L);
        TransferenciaItem ti = new TransferenciaItem();
        ti.setId(5001L);
        ti.setTransferencia(transferencia);
        ti.setPresentacionPreTransferencia(presentacion);
        ti.setCantidadPreTransferencia(3D);
        ti.setUsuario(usuario);
        return ti;
    }

    private void stockEnOrigen(String stock) {
        doReturn(new BigDecimal(stock)).when(service).stockPrevio(PRODUCTO_ID, ORIGEN_ID);
    }

    @Test
    void conStockCeroRegistraEnUnidades() {
        stockEnOrigen("0");

        assertTrue(service.registrarTransferencia(item()));

        // 3 cajas de 12 son 36 unidades; referencia = transferencia, item = el item.
        verify(service).insertarTransferencia(ORIGEN_ID, PRODUCTO_ID, 36D, new BigDecimal("0"), 55L, 900L, 5001L);
    }

    @Test
    void conStockNegativoRegistraElNumeroExacto() {
        stockEnOrigen("-4.125");

        assertTrue(service.registrarTransferencia(item()));

        verify(service).insertarTransferencia(ORIGEN_ID, PRODUCTO_ID, 36D, new BigDecimal("-4.125"), 55L, 900L, 5001L);
    }

    @Test
    void conStockPositivoNoRegistra() {
        stockEnOrigen("0.5");

        assertFalse(service.registrarTransferencia(item()));

        verify(service, never()).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    @Test
    void sinProductoOSinOrigenNoRegistraNiRevienta() {
        TransferenciaItem sinPresentacion = item();
        sinPresentacion.setPresentacionPreTransferencia(null);
        assertFalse(service.registrarTransferencia(sinPresentacion));

        TransferenciaItem sinOrigen = item();
        sinOrigen.getTransferencia().setSucursalOrigen(null);
        assertFalse(service.registrarTransferencia(sinOrigen));

        assertFalse(service.registrarTransferencia(null));
        verify(service, never()).insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());
    }

    @Test
    void siLaBaseFallaAlLeerElStockNoLanza() {
        doThrow(new RuntimeException("base caida")).when(service).stockPrevio(PRODUCTO_ID, ORIGEN_ID);

        assertFalse(service.registrarTransferencia(item()));
    }

    @Test
    void siLaBaseFallaAlInsertarNoLanza() {
        stockEnOrigen("0");
        doThrow(new RuntimeException("timeout")).when(service)
                .insertarTransferencia(anyLong(), anyLong(), anyDouble(), any(), any(), any(), any());

        assertFalse(service.registrarTransferencia(item()));
    }

    @Test
    void elCriterioEsStockPrevioMenorOIgualACero() {
        assertTrue(ControlStockNegativoService.debeRegistrar(BigDecimal.ZERO));
        assertTrue(ControlStockNegativoService.debeRegistrar(new BigDecimal("-0.001")));
        assertFalse(ControlStockNegativoService.debeRegistrar(new BigDecimal("0.001")));
        assertFalse(ControlStockNegativoService.debeRegistrar(null));
    }
}
