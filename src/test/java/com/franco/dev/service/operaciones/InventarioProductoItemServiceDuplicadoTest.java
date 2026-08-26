package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.empresarial.Zona;
import com.franco.dev.repository.operaciones.InventarioProductoItemRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Que es un renglon duplicado en un conteo de inventario.
 *
 * La clave era (inventario, producto, vencimiento) y rechazaba tres cosas
 * legitimas: el mismo producto contado en dos zonas —el caso normal de un
 * inventario por zona—, unidad y caja x12 del mismo producto, y dos renglones
 * sin vencimiento, porque Objects.equals toma dos nulos por iguales.
 *
 * Los datos de cada caso se eligen para que no coincidan entre si: si el codigo
 * leyera un campo por otro, el test lo delata en vez de pasar de casualidad.
 */
class InventarioProductoItemServiceDuplicadoTest {

    private static final Long ZONA_GONDOLA = 91L;
    private static final Long ZONA_DEPOSITO = 92L;
    private static final Long UNIDAD = 9L;
    private static final Long CAJA_X12 = 8L;
    private static final Long PRODUCTO = 200L;
    private static final LocalDateTime NOVIEMBRE = LocalDateTime.of(2026, 11, 20, 0, 0);
    private static final LocalDateTime ENERO = LocalDateTime.of(2027, 1, 5, 0, 0);

    private InventarioProductoItemRepository repository;
    private InventarioProductoItemService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventarioProductoItemRepository.class);
        when(repository.save(any(InventarioProductoItem.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        service = new InventarioProductoItemService(repository, mock(ModificacionService.class));
    }

    private InventarioProductoItem item(Long id, Long zonaId, Long presentacionId, LocalDateTime vencimiento) {
        Zona zona = new Zona();
        zona.setId(zonaId);
        zona.setDescripcion(zonaId.equals(ZONA_GONDOLA) ? "gondola 1" : "deposito");

        Inventario inventario = new Inventario();
        inventario.setId(5L);

        InventarioProducto inventarioProducto = new InventarioProducto();
        inventarioProducto.setId(zonaId);
        inventarioProducto.setZona(zona);
        inventarioProducto.setInventario(inventario);

        Producto producto = new Producto();
        producto.setId(PRODUCTO);

        Presentacion presentacion = new Presentacion();
        presentacion.setId(presentacionId);
        presentacion.setProducto(producto);

        InventarioProductoItem item = new InventarioProductoItem();
        item.setId(id);
        item.setInventarioProducto(inventarioProducto);
        item.setPresentacion(presentacion);
        item.setVencimiento(vencimiento);
        return item;
    }

    /** El mismo renglon, pero atribuido a un lote. */
    private InventarioProductoItem itemConLote(Long id, Long zonaId, Long presentacionId,
                                               LocalDateTime vencimiento, Long loteId, String numeroLote) {
        InventarioProductoItem item = item(id, zonaId, presentacionId, vencimiento);
        Lote lote = new Lote();
        lote.setId(loteId);
        lote.setNumeroLote(numeroLote);
        item.setLote(lote);
        return item;
    }

    @Test
    @DisplayName("el mismo renglon repetido en la zona se rechaza: el conteo saldria doble")
    void mismoRenglonRepetido() {
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(item(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.save(item(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));

        // El mensaje se arma aca porque el frontend es capa de presentacion:
        // sin la zona adentro, el operador no sabe adonde ir.
        assertTrue(error.getMessage().contains("gondola 1"), error.getMessage());
    }

    @Test
    @DisplayName("dos renglones de la misma presentacion SIN vencimiento son el mismo renglon")
    void mismaPresentacionSinVencimiento() {
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(item(1L, ZONA_GONDOLA, UNIDAD, null)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.save(item(null, ZONA_GONDOLA, UNIDAD, null)));

        // Sin fecha que las distinga, el mensaje tiene que decir que hay que
        // cargarsela, no repetir el texto del caso con fecha.
        assertTrue(error.getMessage().contains("sin vencimiento"), error.getMessage());
    }

    @Test
    @DisplayName("unidad y caja x12 del mismo producto conviven aunque ninguna tenga fecha")
    void dosPresentacionesDelMismoProducto() {
        // Era el error reportado: la clave miraba el producto, asi que las dos
        // presentaciones eran el mismo renglon para la validacion.
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(item(1L, ZONA_GONDOLA, UNIDAD, null)));

        assertDoesNotThrow(() -> service.save(item(null, ZONA_GONDOLA, CAJA_X12, null)));
    }

    @Test
    @DisplayName("el mismo producto contado en dos zonas es el caso normal, no un duplicado")
    void mismoProductoEnDosZonas() {
        // Hay stock en gondola y en deposito, y los conteos se suman. Con el
        // alcance en todo el inventario, guardar el segundo fallaba.
        when(repository.findByInventarioProductoId(ZONA_DEPOSITO)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> service.save(item(null, ZONA_DEPOSITO, UNIDAD, NOVIEMBRE)));
    }

    @Test
    @DisplayName("dos lotes de la misma presentacion se distinguen por su fecha")
    void dosLotesConFechasDistintas() {
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(item(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));

        assertDoesNotThrow(() -> service.save(item(null, ZONA_GONDOLA, UNIDAD, ENERO)));
    }

    @Test
    @DisplayName("editar un renglon no choca contra si mismo")
    void editarNoChocaConsigoMismo() {
        // Es lo que corre en cada Guardar conteo del telefono y del escritorio.
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Arrays.asList(item(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));

        assertDoesNotThrow(() -> service.save(item(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));
    }

    @Test
    @DisplayName("dos lotes distintos de la misma presentacion conviven aunque compartan vencimiento")
    void dosLotesDistintosConLaMismaFecha() {
        // Con control de lote un renglon ES un lote, y dos lotes del mismo producto pueden vencer
        // el mismo dia. Sin el lote en la clave, contar el segundo fallaba.
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(
                        itemConLote(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE, 41L, "L-2026-88")));

        assertDoesNotThrow(() -> service.save(
                itemConLote(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE, 42L, "L-2026-91")));
    }

    @Test
    @DisplayName("el mismo lote repetido en la zona se rechaza y el mensaje lo nombra")
    void mismoLoteRepetido() {
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(
                        itemConLote(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE, 41L, "L-2026-88")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.save(
                        itemConLote(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE, 41L, "L-2026-88")));

        assertTrue(error.getMessage().contains("L-2026-88"), error.getMessage());
    }

    @Test
    @DisplayName("un renglon con lote y otro sin lote no son el mismo renglon")
    void conLoteYSinLoteConviven() {
        // El renglon sin lote es la mercaderia que todavia no se atribuyo a ninguno: es
        // exactamente el caso que hay que poder contar aparte.
        when(repository.findByInventarioProductoId(ZONA_GONDOLA))
                .thenReturn(Collections.singletonList(item(1L, ZONA_GONDOLA, UNIDAD, NOVIEMBRE)));

        assertDoesNotThrow(() -> service.save(
                itemConLote(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE, 41L, "L-2026-88")));
    }

    @Test
    @DisplayName("sin zona o sin presentacion no se decide, y se deja pasar")
    void sinDatosNoDecide() {
        // Es lo que hacia la version anterior cuando no podia resolver el
        // inventario o el producto. Endurecerlo ahora rechazaria altas que hoy
        // entran, y este cambio tiene que ser solo una relajacion.
        InventarioProductoItem sinZona = item(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE);
        sinZona.setInventarioProducto(null);
        assertDoesNotThrow(() -> service.save(sinZona));

        InventarioProductoItem sinPresentacion = item(null, ZONA_GONDOLA, UNIDAD, NOVIEMBRE);
        sinPresentacion.setPresentacion(null);
        assertDoesNotThrow(() -> service.save(sinPresentacion));
    }
}
