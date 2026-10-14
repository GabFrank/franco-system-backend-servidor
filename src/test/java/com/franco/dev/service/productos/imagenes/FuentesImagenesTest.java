package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Lectura de la respuesta de cada fuente y armado de la lista que ve el usuario. */
class FuentesImagenesTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return om.readTree(s.replace('\'', '"'));
    }

    @Test
    void searxngLeeLaFotoLaMiniaturaYLaResolucion() throws Exception {
        List<ImagenSugerida> l = SearxngImagenes.parsear(json("{'results':[" +
                "{'url':'https://www.super.com.py/brahma','title':'Brahma pack','img_src':'https://cdn.super.com.py/b.jpg'," +
                "'thumbnail_src':'https://tse.mm.bing.net/th?id=1','resolution':'1200 x 1000'}," +
                "{'url':'https://x.com','img_src':'data:image/png;base64,AAA'}," +
                "{'url':'https://y.com/p','img_src':'https://y.com/i.png','resolution':'sin dato'}]}"));
        assertEquals(2, l.size(), "un img_src que no es http se descarta");
        ImagenSugerida a = l.get(0);
        assertEquals("https://cdn.super.com.py/b.jpg", a.getUrl());
        assertEquals("https://tse.mm.bing.net/th?id=1", a.getMiniatura());
        assertEquals(1200, a.getAncho());
        assertEquals(1000, a.getAlto());
        assertEquals("super.com.py", a.getSitio());
        assertEquals(ImagenSugerida.SEARXNG, a.getFuente());
        ImagenSugerida b = l.get(1);
        assertNull(b.getAncho());
        assertEquals("https://y.com/i.png", b.getMiniatura(), "sin miniatura se usa la foto");
    }

    @Test
    void braveLeePropertiesYThumbnail() throws Exception {
        List<ImagenSugerida> l = BraveImagenes.parsear(json("{'results':[{'title':'Brahma','url':'https://shop.com/p'," +
                "'source':'shop.com','thumbnail':{'src':'https://imgs.search.brave.com/t'}," +
                "'properties':{'url':'https://shop.com/full.jpg','width':800,'height':800}}]}"));
        assertEquals(1, l.size());
        assertEquals("https://shop.com/full.jpg", l.get(0).getUrl());
        assertEquals("https://imgs.search.brave.com/t", l.get(0).getMiniatura());
        assertEquals(800, l.get(0).getAncho());
        assertEquals("shop.com", l.get(0).getSitio());
    }

    @Test
    void openFoodFactsPideLaFotoOriginal() throws Exception {
        List<ImagenSugerida> l = OpenFoodFactsImagenes.parsear(json("{'status':1,'product':{'product_name':'Sub Zero'," +
                "'brands':'Brahma','image_front_url':'https://images.openfoodfacts.org/images/products/784/005/000/2561/front_es.12.400.jpg'}}"));
        assertEquals(1, l.size());
        assertEquals("https://images.openfoodfacts.org/images/products/784/005/000/2561/front_es.12.full.jpg", l.get(0).getUrl());
        assertTrue(l.get(0).getMiniatura().endsWith(".400.jpg"));
        assertEquals("Brahma - Sub Zero", l.get(0).getTitulo());
        assertTrue(OpenFoodFactsImagenes.parsear(json("{'status':0}")).isEmpty(), "código que no conoce");
        assertTrue(OpenFoodFactsImagenes.parsear(null).isEmpty(), "404");
    }

    @Test
    void ordenarSacaChicasYRepetidasYPoneLasCuadradasPrimero() {
        ImagenSugerida chica = img("https://a/chica.jpg", 300, 300);
        ImagenSugerida alargada = img("https://a/banner.jpg", 2000, 600);
        ImagenSugerida sinTamano = img("https://a/sin.jpg", null, null);
        ImagenSugerida cuadrada = img("https://a/cuadrada.jpg", 900, 1000);
        ImagenSugerida repetida = img("http://a/cuadrada.jpg", 900, 1000);
        Set<String> vistas = new HashSet<>();
        List<ImagenSugerida> l = SugerenciaImagenService.ordenar(List.of(chica, alargada, sinTamano, cuadrada, repetida), vistas, 500, 10);
        assertEquals(List.of(cuadrada, sinTamano, alargada), l);
        assertTrue(SugerenciaImagenService.ordenar(List.of(cuadrada), vistas, 500, 10).isEmpty(),
                "lo que ya salió por código no se repite por descripción");
        assertEquals(1, SugerenciaImagenService.ordenar(List.of(img("https://b/1", 800, 800), img("https://b/2", 800, 800)), new HashSet<>(), 500, 1).size());
    }

    @Test
    void elTextoEsProductoMasPresentacionSalvoUnidad() {
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PACK X 6", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PACK X 6")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PROMO UNITARIO PARA CALLE 10", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PROMO UNITARIO PARA CALLE 10")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "UNIDAD")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", null)));
    }

    private static ImagenSugerida img(String url, Integer w, Integer h) {
        return new ImagenSugerida(url, url, w, h, null, null, ImagenSugerida.SEARXNG);
    }

    private static Presentacion pres(String producto, String descripcion) {
        Producto p = new Producto();
        p.setDescripcion(producto);
        Presentacion pr = new Presentacion();
        pr.setProducto(p);
        pr.setDescripcion(descripcion);
        return pr;
    }
}
