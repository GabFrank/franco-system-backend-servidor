package com.franco.dev.service.productos;

import com.franco.dev.repository.productos.ProductoRepository;
import com.franco.dev.service.productos.search.CodigoSearchService;
import com.franco.dev.service.productos.search.ProductoSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La busqueda de productos por texto pide siempre los mismos candidatos, en cualquier pagina (#674).
 *
 * Antes pedia offset + 10 (minimo 50, tope 200): al pasar de pagina entraban mas productos por codigo
 * de barras adelante de los de descripcion, la lista se corria y la pagina siguiente repetia
 * productos. En bodega3, "AGUA" de a 10 daba 149 filas y 70 productos distintos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoSearchVentanaFijaTest {

    @Mock ProductoRepository repository;
    @Mock ProductoSearchService productoSearchService;
    @Mock CodigoSearchService codigoSearchService;
    @InjectMocks ProductoService service;

    @Test
    void cualquierPaginaPideLosMismosCandidatos() {
        ReflectionTestUtils.setField(service, "productoSearchEnabled", true);
        when(productoSearchService.textoBusquedaValido(anyString())).thenReturn(true);
        when(productoSearchService.buscarIdsPorTexto(anyString(), anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(codigoSearchService.buscarProductoIdsPorCoincidencia(anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        for (int offset : new int[]{0, 10, 60, 150, 300}) {
            service.findByAll("AGUA", offset, 1L, false, false, true, null, null);
        }

        verify(productoSearchService, org.mockito.Mockito.times(5))
                .buscarIdsPorTexto(eq("AGUA"), eq(200), eq(true), eq(false), isNull(), isNull());
        verify(codigoSearchService, org.mockito.Mockito.times(5)).buscarProductoIdsPorCoincidencia("AGUA", 200);
    }

    @Test
    void lasConsultasPorCodigoDesempatanPorProducto() {
        Arrays.stream(com.franco.dev.repository.productos.CodigoRepository.class.getMethods())
                .filter(m -> m.getName().startsWith("findProductoIdsByCodigo") && m.isAnnotationPresent(Query.class))
                .map(m -> m.getAnnotation(Query.class).value())
                .forEach(q -> assertTrue(q.contains("ASC, pr.producto_id ASC LIMIT ?2"), q));
    }
}
