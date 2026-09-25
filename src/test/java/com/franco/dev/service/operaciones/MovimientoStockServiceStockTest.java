package com.franco.dev.service.operaciones;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.dto.StockPorSucursalDto;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Existencia total de un producto: una consulta agrupada, no una por sucursal.
 *
 * El contrato que fijan estos tests es doble. El resultado tiene que ser el mismo que daba el bucle
 * {@code for (sucursal) stockByProductoIdAndSucursalId(...)} —incluidas sus rarezas: las sucursales
 * sin movimientos valen cero y los movimientos de una sucursal que no está en la tabla no se suman—
 * y el costo tiene que ser una sola consulta, porque de eso se trataba el cambio. Por eso los
 * {@code verify} de cantidad de llamadas son parte del test y no un adorno: si alguien vuelve a
 * iterar sucursal por sucursal, el resultado sigue dando bien y solo esto lo delata.
 */
class MovimientoStockServiceStockTest {

    private static final double DELTA = 0.0001;
    private static final long PRODUCTO_ID = 77L;

    private MovimientoStockRepository repository;
    private SucursalService sucursalService;
    private MovimientoStockService service;

    @BeforeEach
    void setUp() {
        repository = mock(MovimientoStockRepository.class);
        sucursalService = mock(SucursalService.class);
        // @AllArgsConstructor (orden de declaración): repository, modificacionService, sucursalService,
        // movimientoStockLoteService, loteFefoService, transferenciaItemLoteService.
        service = new MovimientoStockService(
                repository,
                mock(ModificacionService.class),
                sucursalService,
                mock(MovimientoStockLoteService.class),
                mock(LoteFefoService.class),
                mock(TransferenciaItemLoteService.class));
    }

    private Sucursal sucursal(long id, String nombre) {
        Sucursal s = new Sucursal();
        s.setId(id);
        s.setNombre(nombre);
        return s;
    }

    /** Las tres sucursales del escenario base: dos reales y la pseudo-sucursal de clearing. */
    private void conSucursales() {
        when(sucursalService.findAll2()).thenReturn(Arrays.asList(
                sucursal(1L, "SUC. CENTRAL"),
                sucursal(2L, "SUC. ROTONDA"),
                sucursal(999L, "COMPRAS")));
    }

    private void conStock(StockPorSucursalDto... filas) {
        when(repository.stockPorSucursales(PRODUCTO_ID)).thenReturn(Arrays.asList(filas));
    }

    @Test
    void sumaTodasLasSucursalesConUnaSolaConsulta() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0),
                 new StockPorSucursalDto(2L, 5.5),
                 new StockPorSucursalDto(999L, 100.0));

        assertEquals(115.5, service.stockByProductoId(PRODUCTO_ID), DELTA);

        verify(repository, times(1)).stockPorSucursales(PRODUCTO_ID);
        verify(repository, never()).stockByProductoIdAndSucursalId(anyLong(), anyLong());
        verify(sucursalService, times(1)).findAll2();
    }

    @Test
    void excluyeLaPseudoSucursalPorNombreSinImportarMayusculas() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0),
                 new StockPorSucursalDto(2L, 5.5),
                 new StockPorSucursalDto(999L, 100.0));

        double stock = service.stockByProductoIdExcluyendoNombresSucursal(
                PRODUCTO_ID, Collections.singletonList("compras"));

        assertEquals(15.5, stock, DELTA);
        verify(repository, times(1)).stockPorSucursales(PRODUCTO_ID);
        verify(repository, never()).stockByProductoIdAndSucursalId(anyLong(), anyLong());
    }

    /** Una sucursal sin movimientos no vuelve en el GROUP BY; el bucle viejo la sumaba como cero. */
    @Test
    void laSucursalSinMovimientosCuentaComoCero() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0));

        assertEquals(10.0, service.stockByProductoId(PRODUCTO_ID), DELTA);
    }

    /** Sin filas no hay stock, y el resultado es 0.0 y no null: hay callers que lo suman directo. */
    @Test
    void sinMovimientosDevuelveCeroYNoNull() {
        conSucursales();
        conStock();

        assertEquals(0.0, service.stockByProductoId(PRODUCTO_ID), DELTA);
    }

    /**
     * Movimientos de una sucursal que ya no está en la tabla: el bucle viejo nunca los preguntaba,
     * así que no entraban en el total. La consulta agrupada sí los trae, y hay que descartarlos para
     * no cambiar el número.
     */
    @Test
    void ignoraLosMovimientosDeUnaSucursalDesconocida() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0),
                 new StockPorSucursalDto(4242L, 999.0));

        assertEquals(10.0, service.stockByProductoId(PRODUCTO_ID), DELTA);
    }

    /** Defensa contra una fila con cantidad nula: suma cero en vez de romper con NPE. */
    @Test
    void unaCantidadNulaNoRompeLaSuma() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0),
                 new StockPorSucursalDto(2L, null));

        assertEquals(10.0, service.stockByProductoId(PRODUCTO_ID), DELTA);
    }

    /**
     * La lista vacía de exclusiones se comporta igual que no excluir nada, que es lo que hacía el
     * bucle viejo con su {@code anyMatch} sobre una lista vacía.
     */
    @Test
    void listaDeExclusionesVaciaNoExcluyeNada() {
        conSucursales();
        conStock(new StockPorSucursalDto(1L, 10.0),
                 new StockPorSucursalDto(999L, 100.0));

        double stock = service.stockByProductoIdExcluyendoNombresSucursal(
                PRODUCTO_ID, Collections.<String>emptyList());

        assertEquals(110.0, stock, DELTA);
    }

    /**
     * El camino viejo pasaba cada subtotal por {@code Float} —la firma del repositorio devolvía
     * {@code Float}— y recién ahí lo ensanchaba a {@code double}. Ese redondeo entra en el
     * denominador del costo medio, así que se conserva tal cual: el objetivo era sacar consultas,
     * no mover números. 0.1 sumado en tres sucursales da el mismo 0.30000001192092896 que antes,
     * y no el 0.30000000000000004 de la suma en {@code double}.
     */
    @Test
    void conservaElRedondeoAFloatDelCaminoViejo() {
        when(sucursalService.findAll2()).thenReturn(Arrays.asList(
                sucursal(1L, "A"), sucursal(2L, "B"), sucursal(3L, "C")));
        conStock(new StockPorSucursalDto(1L, 0.1),
                 new StockPorSucursalDto(2L, 0.1),
                 new StockPorSucursalDto(3L, 0.1));

        double esperado = 0.0;
        for (int i = 0; i < 3; i++) {
            Float subtotal = 0.1f;
            esperado += Double.valueOf(subtotal);
        }

        assertEquals(esperado, service.stockByProductoId(PRODUCTO_ID), 0.0);
    }

    /** Sin sucursales cargadas no hay nada que sumar, aunque la consulta traiga filas. */
    @Test
    void sinSucursalesElTotalEsCero() {
        when(sucursalService.findAll2()).thenReturn(Collections.<Sucursal>emptyList());
        conStock(new StockPorSucursalDto(1L, 10.0));

        assertEquals(0.0, service.stockByProductoId(PRODUCTO_ID), DELTA);
    }

    /**
     * Una sucursal sin nombre no puede coincidir con ningún nombre excluido; el bucle viejo la
     * conservaba porque {@code "COMPRAS".equalsIgnoreCase(null)} es false.
     */
    @Test
    void laSucursalSinNombreNoSeExcluye() {
        when(sucursalService.findAll2()).thenReturn(Collections.singletonList(sucursal(1L, null)));
        conStock(new StockPorSucursalDto(1L, 10.0));

        List<String> excluidos = Collections.singletonList("COMPRAS");
        assertEquals(10.0,
                service.stockByProductoIdExcluyendoNombresSucursal(PRODUCTO_ID, excluidos), DELTA);
    }
}
