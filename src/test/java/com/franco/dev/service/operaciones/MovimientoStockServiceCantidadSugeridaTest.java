package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.CantidadSugeridaPorSucursalDto;
import com.franco.dev.domain.operaciones.dto.ComprasPorSucursalDto;
import com.franco.dev.domain.operaciones.dto.VentasPorSucursalDto;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Insumos de la cantidad sugerida: dos consultas agrupadas, no una por sucursal.
 *
 * El diálogo de ítem de compra necesita, por sucursal, cuatro números —cuánto se vendió, cuántas
 * compras hubo y entre qué fechas—, y hasta ahora los sacaba bajándose hasta 1000 movimientos por
 * sucursal y por tipo, en 2N requests encadenados. Acá se fija el contrato del reemplazo.
 *
 * El {@code verify} de cantidad de llamadas es parte del test y no un adorno: si alguien vuelve a
 * consultar sucursal por sucursal, el resultado sigue dando bien y solo eso lo delata.
 */
class MovimientoStockServiceCantidadSugeridaTest {

    private static final double DELTA = 0.0001;
    private static final long PRODUCTO_ID = 77L;
    private static final LocalDateTime INICIO = LocalDateTime.of(2025, 9, 1, 0, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2025, 9, 30, 23, 59, 59);

    private MovimientoStockRepository repository;
    private MovimientoStockService service;

    @BeforeEach
    void setUp() {
        repository = mock(MovimientoStockRepository.class);
        service = new MovimientoStockService(
                repository,
                mock(ModificacionService.class),
                mock(SucursalService.class),
                mock(MovimientoStockLoteService.class),
                mock(LoteFefoService.class),
                mock(TransferenciaItemLoteService.class));
    }

    private void responde(List<VentasPorSucursalDto> ventas, List<ComprasPorSucursalDto> compras) {
        when(repository.ventasPorSucursal(anyLong(), any(), any(), any())).thenReturn(ventas);
        when(repository.comprasPorSucursal(anyLong(), any(), any(), any())).thenReturn(compras);
    }

    private List<CantidadSugeridaPorSucursalDto> ejecutar(List<Long> sucursales) {
        return service.cantidadSugeridaPorSucursales(PRODUCTO_ID, INICIO, FIN, sucursales);
    }

    @Test
    void mergeaVentasYComprasDeLaMismaSucursal() {
        LocalDateTime primera = LocalDateTime.of(2025, 9, 2, 10, 0);
        LocalDateTime ultima = LocalDateTime.of(2025, 9, 26, 10, 0);
        responde(
                Collections.singletonList(new VentasPorSucursalDto(1L, 120.0)),
                Collections.singletonList(new ComprasPorSucursalDto(1L, 4L, primera, ultima)));

        List<CantidadSugeridaPorSucursalDto> filas = ejecutar(Collections.singletonList(1L));

        assertEquals(1, filas.size());
        CantidadSugeridaPorSucursalDto fila = filas.get(0);
        assertEquals(1L, fila.getSucursalId());
        assertEquals(120.0, fila.getTotalVentas(), DELTA);
        assertEquals(4L, fila.getCantidadCompras());
        assertEquals(primera, fila.getPrimeraCompra());
        assertEquals(ultima, fila.getUltimaCompra());
    }

    @Test
    void sucursalSoloConVentasNoTieneCompras() {
        responde(
                Collections.singletonList(new VentasPorSucursalDto(2L, 50.0)),
                Collections.emptyList());

        CantidadSugeridaPorSucursalDto fila = ejecutar(Collections.singletonList(2L)).get(0);

        assertEquals(50.0, fila.getTotalVentas(), DELTA);
        assertEquals(0L, fila.getCantidadCompras());
        assertNull(fila.getPrimeraCompra());
        assertNull(fila.getUltimaCompra());
    }

    @Test
    void sucursalSoloConComprasTieneVentasEnCero() {
        LocalDateTime fecha = LocalDateTime.of(2025, 9, 5, 8, 30);
        responde(
                Collections.emptyList(),
                Collections.singletonList(new ComprasPorSucursalDto(3L, 1L, fecha, fecha)));

        CantidadSugeridaPorSucursalDto fila = ejecutar(Collections.singletonList(3L)).get(0);

        assertEquals(0.0, fila.getTotalVentas(), DELTA);
        assertEquals(1L, fila.getCantidadCompras());
        assertEquals(fecha, fila.getUltimaCompra());
    }

    /**
     * Una sucursal sin movimientos en el rango no vuelve en ningun GROUP BY. El diálogo la muestra
     * en cero por su cuenta, igual que hace con el stock desde #208.
     */
    @Test
    void sucursalSinMovimientosNoVuelveEnLaLista() {
        responde(Collections.emptyList(), Collections.emptyList());

        assertTrue(ejecutar(Arrays.asList(1L, 2L, 3L)).isEmpty());
    }

    @Test
    void ordenaPorSucursalParaQueElResultadoSeaEstable() {
        responde(
                Arrays.asList(new VentasPorSucursalDto(9L, 1.0), new VentasPorSucursalDto(2L, 1.0)),
                Collections.singletonList(new ComprasPorSucursalDto(5L, 1L, INICIO, INICIO)));

        List<CantidadSugeridaPorSucursalDto> filas = ejecutar(Arrays.asList(9L, 2L, 5L));

        assertEquals(Arrays.asList(2L, 5L, 9L),
                filas.stream().map(CantidadSugeridaPorSucursalDto::getSucursalId).collect(java.util.stream.Collectors.toList()));
    }

    /**
     * El punto del cambio: el costo no depende de cuantas sucursales pida el diálogo.
     */
    @Test
    void consultaDosVecesSinImportarCuantasSucursales() {
        responde(Collections.emptyList(), Collections.emptyList());

        ejecutar(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        verify(repository, times(1)).ventasPorSucursal(anyLong(), any(), any(), any());
        verify(repository, times(1)).comprasPorSucursal(anyLong(), any(), any(), any());
    }
}
