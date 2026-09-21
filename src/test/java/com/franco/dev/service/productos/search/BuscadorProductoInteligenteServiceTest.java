package com.franco.dev.service.productos.search;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.dto.BuscadorProductoFiltros;
import com.franco.dev.domain.productos.dto.BuscadorProductoResultado;
import com.franco.dev.domain.productos.enums.TipoCoincidenciaBuscador;
import com.franco.dev.repository.productos.ProductoProveedorRepository;
import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.productos.ProductoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * El buscador de compras tiene que encontrar un producto por un número de su descripción.
 *
 * Antes, todo término de 3+ dígitos se trataba como código de barras y nunca llegaba a la
 * búsqueda por texto: «ARCOR HELADO MOGUL ... 50 GR 1014218» (producto 7108 de bodega) no salía
 * escribiendo 1014218 en compras, aunque la lista de productos sí lo encontraba.
 */
class BuscadorProductoInteligenteServiceTest {

    @Mock private ProductoService productoService;
    @Mock private ProductoRepository productoRepository;
    @Mock private ProductoSearchService productoSearchService;
    @Mock private CodigoSearchService codigoSearchService;
    @Mock private ProductoProveedorRepository productoProveedorRepository;

    @InjectMocks private BuscadorProductoInteligenteService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "productoSearchEnabled", true);
        when(productoSearchService.textoBusquedaValido(anyString())).thenReturn(true);
        when(productoRepository.findById(any())).thenReturn(Optional.empty());
        when(codigoSearchService.buscarProductoIdsPorCoincidencia(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(productoSearchService.buscarIdsPorTexto(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void numeroQueSoloEstaEnLaDescripcionSeEncuentraPorTexto() {
        Producto mogul = producto(7108L);
        when(productoSearchService.buscarIdsPorTexto(eq("1014218"), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(7108L));

        List<BuscadorProductoResultado> resultados = buscar("1014218");

        assertEquals(1, resultados.size());
        assertEquals(mogul, resultados.get(0).getProducto());
        assertEquals(TipoCoincidenciaBuscador.TEXTO, resultados.get(0).getTipoCoincidencia());
    }

    @Test
    void conLuceneApagadoElNumeroSeBuscaEnLaDescripcionPorSql() {
        ReflectionTestUtils.setField(service, "productoSearchEnabled", false);
        Producto mogul = producto(7108L);
        when(productoRepository.findIdsByDescripcionLike(eq("1014218"), anyInt())).thenReturn(List.of(7108L));

        List<BuscadorProductoResultado> resultados = buscar("1014218");

        assertEquals(1, resultados.size());
        assertEquals(mogul, resultados.get(0).getProducto());
        assertEquals(TipoCoincidenciaBuscador.TEXTO, resultados.get(0).getTipoCoincidencia());
    }

    @Test
    void elCodigoExactoQuedaPrimeroYElTextoDespues() {
        Producto exacto = producto(1L);
        producto(2L);
        when(productoService.findByCodigo("7790580142186")).thenReturn(exacto);
        when(productoSearchService.buscarIdsPorTexto(eq("7790580142186"), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(2L));

        List<BuscadorProductoResultado> resultados = buscar("7790580142186");

        assertEquals(List.of(1L, 2L), ids(resultados));
        assertEquals(TipoCoincidenciaBuscador.CODIGO_EXACTO, resultados.get(0).getTipoCoincidencia());
        assertEquals(TipoCoincidenciaBuscador.TEXTO, resultados.get(1).getTipoCoincidencia());
    }

    @Test
    void elMismoProductoPorCodigoYPorDescripcionApareceUnaSolaVezComoCodigo() {
        producto(7108L);
        when(codigoSearchService.buscarProductoIdsPorCoincidencia(eq("142186"), anyInt()))
                .thenReturn(List.of(7108L));
        when(productoSearchService.buscarIdsPorTexto(eq("142186"), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(7108L));

        List<BuscadorProductoResultado> resultados = buscar("142186");

        assertEquals(List.of(7108L), ids(resultados));
        assertEquals(TipoCoincidenciaBuscador.CODIGO_PARCIAL, resultados.get(0).getTipoCoincidencia());
    }

    private List<BuscadorProductoResultado> buscar(String texto) {
        return service.buscar(BuscadorProductoFiltros.builder()
                .texto(texto)
                .activo(true)
                .page(0)
                .size(20)
                .build()).getContent();
    }

    private Producto producto(Long id) {
        Producto producto = new Producto();
        producto.setId(id);
        when(productoRepository.findById(id)).thenReturn(Optional.of(producto));
        return producto;
    }

    private List<Long> ids(List<BuscadorProductoResultado> resultados) {
        return resultados.stream().map(r -> r.getProducto().getId()).collect(Collectors.toList());
    }
}
