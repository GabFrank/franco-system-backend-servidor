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

    /** Recorte del HTML real de /images/async (2026-10-14). */
    private static final String BING_HTML = "<ul class=\"dgControl_list\"><li data-idx=\"1\"><div class=\"imgpt\">"
            + "<a aria-label=\"Resultado de imagen\" role=\"link\" class=\"iusc\" style=\"height:210px;width:210px\" "
            + "m=\"{&quot;purl&quot;:&quot;https://untappd.com/b/brahma-sub-zero/205121/photos&quot;,"
            + "&quot;murl&quot;:&quot;https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg&quot;,"
            + "&quot;turl&quot;:&quot;https://ts1.mm.bing.net/th?id=OIP.b8fz&amp;pid=15.1&quot;,"
            + "&quot;t&quot;:&quot;Brahma Sub Zero &#8211; Cervecer&#237;a Paraguaya&quot;}\" data-fnvg=\"1\" "
            + "href=\"/images/search?view=detailV2&amp;mediaurl=x&amp;exph=640&amp;expw=640&amp;q=BRAHMA\" h=\"ID=images\">"
            + "<img class=\"mimg\" src=\"https://th.bing.com/x\"/></a></div></li>"
            + "<li><a class=\"iusc\" m=\"{&quot;murl&quot;:&quot;data:image/png;base64,AAA&quot;}\" href=\"/x\"></a></li>"
            + "<li><a class=\"iusc\" m=\"{&quot;murl&quot;:&quot;https://y.com/i.png&quot;}\" href=\"/images/search?q=x\"></a></li></ul>";

    @Test
    void bingLeeElJsonDelEnlaceYElTamanoDelHref() {
        List<ImagenSugerida> l = BingImagenes.parsear(BING_HTML, om);
        assertEquals(2, l.size(), "un murl que no es http se descarta");
        ImagenSugerida a = l.get(0);
        assertEquals("https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg", a.getUrl());
        assertEquals("https://ts1.mm.bing.net/th?id=OIP.b8fz&pid=15.1", a.getMiniatura());
        assertEquals(640, a.getAncho());
        assertEquals(640, a.getAlto());
        assertEquals("Brahma Sub Zero \u2013 Cervecer\u00eda Paraguaya", a.getTitulo());
        assertEquals("untappd.com", a.getSitio());
        assertEquals(ImagenSugerida.BING, a.getFuente());
        ImagenSugerida b = l.get(1);
        assertNull(b.getAncho(), "sin expw/exph no se inventa el tamaño");
        assertEquals("https://y.com/i.png", b.getMiniatura(), "sin miniatura se usa la foto");
        assertEquals("y.com", b.getSitio());
        assertTrue(BingImagenes.parsear("<html>captcha</html>", om).isEmpty(), "si Bing cambia el HTML no rompe, no trae nada");
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
    void porCodigoSeBuscaConLaMarcaYSeMuestranLosQueNombranElCodigoMasLosPrimeros() {
        assertEquals("7840050002561 BRAHMA", SugerenciaImagenService.consultaPorCodigo("7840050002561", "BRAHMA SUB ZERO LATA 269 ML"));
        assertEquals("7840050002561", SugerenciaImagenService.consultaPorCodigo("7840050002561", ""));

        ImagenSugerida primero = conPagina("https://biggie.com.py/b.jpg", "https://biggie.com.py/item/cerveza-brahma", "Cerveza Brahma SubZero");
        ImagenSugerida ruido1 = conPagina("https://x.com/1.jpg", "https://x.com/raid", "Insecticida");
        ImagenSugerida ruido2 = conPagina("https://x.com/2.jpg", "https://x.com/mousse", "Mousse");
        ImagenSugerida ruido3 = conPagina("https://x.com/3.jpg", "https://x.com/perfume", "Perfume");
        ImagenSugerida nombra = conPagina("https://c.com/f.jpg", "https://cosmos.com/produtos/7840050002561-brahma", "Brahma");
        ImagenSugerida chica = conPagina("https://c.com/chica.jpg", "https://c.com/7840050002561", "Brahma");
        chica.setAncho(100);
        chica.setAlto(100);
        List<ImagenSugerida> l = SugerenciaImagenService.filtrarPorCodigo(
                List.of(primero, ruido1, ruido2, ruido3, nombra, chica), "7840050002561", new HashSet<>(), 300, 20);
        assertEquals(List.of(nombra, primero, ruido1, ruido2), l, "los que nombran el código primero y del resto solo 3");
    }

    @Test
    void elTextoEsProductoMasPresentacionSalvoUnidad() {
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PACK X 6", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PACK X 6")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PROMO UNITARIO PARA CALLE 10", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PROMO UNITARIO PARA CALLE 10")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "UNIDAD")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", null)));
    }

    private static ImagenSugerida img(String url, Integer w, Integer h) {
        return new ImagenSugerida(url, url, w, h, null, null, null, ImagenSugerida.BING);
    }

    private static ImagenSugerida conPagina(String url, String pagina, String titulo) {
        return new ImagenSugerida(url, url, null, null, titulo, null, pagina, ImagenSugerida.BING);
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
