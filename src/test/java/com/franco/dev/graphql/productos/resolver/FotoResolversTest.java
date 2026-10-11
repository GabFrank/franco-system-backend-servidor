package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.dto.ProductoVencidoViewDTO;
import com.franco.dev.graphql.operaciones.resolver.ProductoSaldoDtoResolver;
import com.franco.dev.graphql.operaciones.resolver.ProductoVencidoViewDTOResolver;
import com.franco.dev.service.productos.FotoProductoService;
import com.franco.dev.service.productos.FotoProductoService.Tamano;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Issue #263: los cuatro tipos que exponen la foto la resuelven por la misma regla
 * ({@link FotoProductoService}) y cada campo pide el tamano que su nombre promete. Un campo del
 * schema sin su metodo en el resolver no rompe el build: tumba el arranque.
 */
class FotoResolversTest {

    private static final String GRAPHQL = "src/main/resources/graphql/";

    private final FotoProductoService fotos = mock(FotoProductoService.class);

    @Test
    void productoPideCadaTamanoPorSuCampo() {
        ProductoResolver resolver = con(new ProductoResolver());
        Producto producto = new Producto();
        producto.setId(70L);
        when(fotos.deProducto(70L, Tamano.ORIGINAL)).thenReturn("original");
        when(fotos.deProducto(70L, Tamano.MINIATURA)).thenReturn("miniatura");
        when(fotos.deProducto(70L, Tamano.MEDIANA)).thenReturn("mediana");

        assertEquals("original", resolver.imagenPrincipal(producto));
        assertEquals("miniatura", resolver.imagenPrincipalMiniatura(producto));
        assertEquals("mediana", resolver.imagenPrincipalMediana(producto));
    }

    @Test
    void laMedianaDeLaPresentacionSinFotoEsNullYNoElRellenoGris() {
        PresentacionResolver resolver = con(new PresentacionResolver());
        Presentacion presentacion = new Presentacion();
        presentacion.setId(7L);

        assertNull(resolver.imagenPrincipalMediana(presentacion));
        verify(fotos).dePresentacion(7L, Tamano.MEDIANA);
    }

    @Test
    void elSaldoResuelveLaFotoDeSuProducto() {
        ProductoSaldoDtoResolver resolver = con(new ProductoSaldoDtoResolver());
        ProductoSaldoDto fila = new ProductoSaldoDto();
        fila.setProductoId(70L);
        when(fotos.deProducto(70L, Tamano.MINIATURA)).thenReturn("miniatura");
        when(fotos.deProducto(70L, Tamano.ORIGINAL)).thenReturn("original");

        assertEquals("miniatura", resolver.imagenPrincipalMiniatura(fila));
        assertEquals("original", resolver.imagenPrincipal(fila));
        assertNull(resolver.imagenPrincipalMiniatura(null));
    }

    @Test
    void elVencimientoPideLaFotoDeLaPresentacionVencidaConElProductoDeRespaldo() {
        ProductoVencidoViewDTOResolver resolver = con(new ProductoVencidoViewDTOResolver());
        ProductoVencidoViewDTO fila = new ProductoVencidoViewDTO();
        fila.setPresentacionId(8L);
        fila.setProductoId(70L);
        when(fotos.deVencimiento(8L, 70L, Tamano.MINIATURA)).thenReturn("miniatura");
        when(fotos.deVencimiento(8L, 70L, Tamano.ORIGINAL)).thenReturn("original");

        assertEquals("miniatura", resolver.imagenPrincipalMiniatura(fila));
        assertEquals("original", resolver.imagenPrincipal(fila));
        assertNull(resolver.imagenPrincipalMiniatura(null));
    }

    @Test
    void cadaCampoDeFotoDelSchemaTieneSuMetodoEnElResolver() throws Exception {
        exigir("productos/producto/productos.graphqls", "Producto", ProductoResolver.class, Producto.class,
                "imagenPrincipal", "imagenPrincipalMiniatura", "imagenPrincipalMediana");
        exigir("productos/producto/presentacion.graphqls", "Presentacion", PresentacionResolver.class, Presentacion.class,
                "imagenPrincipal", "imagenPrincipalMediana");
        exigir("operaciones/movimiento-stock.graphqls", "ProductoSaldoDto", ProductoSaldoDtoResolver.class,
                ProductoSaldoDto.class, "imagenPrincipal", "imagenPrincipalMiniatura");
        exigir("operaciones/producto-vencido.graphqls", "ProductoVencidoView", ProductoVencidoViewDTOResolver.class,
                ProductoVencidoViewDTO.class, "imagenPrincipal", "imagenPrincipalMiniatura");
    }

    private void exigir(String archivo, String tipo, Class<?> resolver, Class<?> fuente, String... campos) throws Exception {
        String schema = new String(Files.readAllBytes(Paths.get(GRAPHQL + archivo)), StandardCharsets.UTF_8);
        Matcher bloque = Pattern.compile("type\\s+" + tipo + "\\s*\\{(.*?)\\n\\}", Pattern.DOTALL).matcher(schema);
        assertTrue(bloque.find(), "no se encontro el type " + tipo + " en " + archivo);
        for (String campo : campos) {
            assertTrue(Pattern.compile("^\\s*" + campo + "\\s*:\\s*String\\s*$", Pattern.MULTILINE).matcher(bloque.group(1)).find(),
                    tipo + "." + campo + " no esta en el schema como String nullable");
            assertEquals(String.class, resolver.getMethod(campo, fuente).getReturnType(), tipo + "." + campo);
        }
    }

    private <T> T con(T resolver) {
        ReflectionTestUtils.setField(resolver, "fotoProductoService", fotos);
        return resolver;
    }
}
