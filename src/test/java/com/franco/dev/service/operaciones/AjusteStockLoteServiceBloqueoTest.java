package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import com.franco.dev.graphql.operaciones.input.AjusteStockLoteInput;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * #463: el ajuste por lote toma el lock de la sucursal antes de resolver el lote (que puede
 * insertarlo) y antes de leer su saldo. Leído sin lock, el ajuste se calcula contra un saldo que una
 * transferencia en paralelo puede estar cambiando.
 */
@ExtendWith(MockitoExtension.class)
class AjusteStockLoteServiceBloqueoTest {

    @Mock
    private MovimientoStockService movimientoStockService;
    @Mock
    private MovimientoStockLoteService movimientoStockLoteService;
    @Mock
    private LoteService loteService;
    @Mock
    private LoteFefoService loteFefoService;
    @Mock
    private ProductoService productoService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private ModificacionService modificacionService;

    @InjectMocks
    private AjusteStockLoteService service;

    @Test
    void tomaElLockAntesDeResolverElLoteYDeLeerElSaldo() {
        AjusteStockLoteInput input = new AjusteStockLoteInput();
        input.setProductoId(4680L);
        input.setSucursalId(13L);
        input.setModo(ModoAjusteLote.ATRIBUIR);
        input.setCantidadFinal(30.0);
        input.setNumeroLote("REPRO463");

        // Si el lock no se consigue, no se tocó nada: ni el producto, ni el lote, ni el ledger.
        IllegalStateException sinLock = new IllegalStateException("sin lock");
        doThrow(sinLock).when(loteFefoService).bloquearSaldo(13L);

        assertSame(sinLock, assertThrows(IllegalStateException.class, () -> service.ajustar(input)));

        verifyNoInteractions(productoService, loteService, movimientoStockLoteService, movimientoStockService);
    }
}
