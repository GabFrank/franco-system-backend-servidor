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
        ImagenSugerida relacionada = conPagina("https://c.com/otro.jpg", "https://cosmos.com/produtos/7840050002561-brahma", "Pepsi");
        ImagenSugerida icono = conPagina("https://c.com/icono.jpg", "https://c.com/7840050002561", "Brahma");
        icono.setAncho(60);
        icono.setAlto(60);
        ImagenSugerida exactaChica = conPagina("https://salemmaonline.com.py/products/7840050002561.jpg", "https://salemmaonline.com.py/x", "7840050002561");
        exactaChica.setAncho(200);
        exactaChica.setAlto(400);
        ImagenSugerida ruidoChico = conPagina("https://x.com/4.jpg", "https://x.com/chico", "Brahma");
        ruidoChico.setAncho(200);
        ruidoChico.setAlto(200);
        List<ImagenSugerida> l = SugerenciaImagenService.filtrarPorCodigo(
                List.of(ruidoChico, primero, ruido1, ruido2, ruido3, nombra, relacionada, icono, exactaChica), "7840050002561", new HashSet<>(), 300, 20);
        assertEquals(List.of(exactaChica, nombra), l,
                "la foto que nombra el código (aunque sea chica) y una sola de cada página que lo nombra; con exactas, nada del resto");
        assertTrue(exactaChica.getExacta(), "la foto nombra el código: es exacta");
        assertNull(nombra.getExacta(), "solo la página nombra el código: puede ser otro producto de la página");
        assertEquals(List.of(nombra, primero, ruido1, ruido2), SugerenciaImagenService.filtrarPorCodigo(
                List.of(ruidoChico, primero, ruido1, ruido2, ruido3, nombra, relacionada), "7840050002561", new HashSet<>(), 300, 20),
                "sin exactas: la de la página y del resto los 3 primeros del tamaño mínimo");
        assertNull(primero.getExacta());
    }

    @Test
    void elTextoEsProductoMasPresentacionSalvoUnidad() {
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PACK X 6", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PACK X 6")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML PROMO UNITARIO PARA CALLE 10", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "PROMO UNITARIO PARA CALLE 10")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", "UNIDAD")));
        assertEquals("BRAHMA SUB ZERO LATA 269 ML", SugerenciaImagenService.textoSugerido(pres("BRAHMA SUB ZERO LATA 269 ML", null)));
    }

    @Test
    void packSeDetectaPorLaCantidadDeLaPresentacionOPorElTextoDelUsuario() {
        BusquedaPack p = BusquedaPack.detectar("BRAHMA SUB ZERO LATA 269 ML PACK X 12 UNIDAD", 12d, false);
        assertEquals(12, p.cantidad);
        assertEquals("pack", p.palabra);
        assertEquals(3, BusquedaPack.detectar("BRAHMA SUB ZERO LATA 269 ML 3 X 10", 3d, false).cantidad,
                "con el texto del central manda la cantidad de la presentación, no el precio");
        assertNull(BusquedaPack.detectar("BRAHMA SUB ZERO LATA 269 ML", 1d, false));
        assertNull(BusquedaPack.detectar("BRAHMA SUB ZERO LATA 269 ML", null, false));

        BusquedaPack caja = BusquedaPack.detectar("CERVEZA BRAHMA SUB ZERO LATA 269 ML X 12 UNID.", null, true);
        assertEquals(12, caja.cantidad, "texto del usuario: la cantidad sale del texto");
        assertEquals("caja", BusquedaPack.detectar("GALLETITA OREO CAJA X 24", 1d, true).palabra);
        assertEquals(12, BusquedaPack.detectar("CERV BRAHMA ZERO 350ML C/12", null, true).cantidad);
        assertNull(BusquedaPack.detectar("Brahma Sub Zero Lata 269cc", 12d, true), "el usuario cambió el texto: no se usa la cantidad");
    }

    @Test
    void laConsultaDePackDejaElProductoYSumaLaCantidad() {
        BusquedaPack p = BusquedaPack.detectar("x", 12d, false);
        assertEquals("BRAHMA SUB ZERO pack 12 latas", p.consulta("BRAHMA SUB ZERO LATA 269 ML PACK X 12 UNIDAD"));
        assertEquals("BRAHMA SUB ZERO pack 12 latas", p.consulta("BRAHMA SUB ZERO LATA 269 ML PROMO PACK X 12 PARA CALLE 10"));
        assertEquals("COCA COLA pack 12 botellas", p.consulta("COCA COLA 1.5L BOTELLA X12"));
        assertEquals("CERV BRAHMA ZERO pack 12", p.consulta("CERV BRAHMA ZERO 350ML C/12"));
        assertEquals("caja 24", BusquedaPack.detectar("OREO CAJA X 24", null, true).consulta("OREO CAJA X 24").replace("OREO ", ""));
    }

    @Test
    void enElPackSoloEntranLasQueNombranLaMarca() {
        String texto = "BRAHMA SUB ZERO LATA 269 ML PACK X 12 UNIDAD";
        assertTrue(BusquedaPack.nombraMarca(conPagina("https://a/1.jpg", null, "Cerveja Brahma Zero Lata 350ml Pack C/12"), texto));
        assertTrue(BusquedaPack.nombraMarca(conPagina("https://a/1.jpg", "https://t.com/brahma-pack", "Pack x 12"), texto), "por la página");
        assertFalse(BusquedaPack.nombraMarca(conPagina("https://a/2.jpg", "https://drinkbartenders.com.br/amstel",
                "Amstel Lata 269 ml pack com 12 unidades"), texto));
        assertTrue(BusquedaPack.nombraMarca(conPagina("https://a/3.jpg", null, "Café Pilão x 12"), "CAFE PILAO 500 G X 12"), "sin acentos");
        assertTrue(BusquedaPack.nombraMarca(conPagina("https://a/4.jpg", null, "7up pack"), "7UP 500 ML X 12"),
                "si no hay palabras para comparar no se filtra");
    }

    @Test
    void elTituloNombraLaCantidadDelPack() {
        BusquedaPack p = BusquedaPack.detectar("x", 12d, false);
        for (String t : List.of("CERVEZA BRAHMA SUB ZERO LATA 269 ML X 12 UNID.", "CERV BRAHMA ZERO 350ML C/12",
                "Cerveja Brahma Zero Lata 350ml Pack C/ 12und", "Cerveja Brahma Zero Lata (12X350ML)",
                "Pack Cerveja Brahma Zero Lata 350ml Com 12 Unidades", "Brahma pack 12 latas", "Brahma 12-pack")) {
            assertTrue(p.nombraCantidad(conPagina("https://a/x.jpg", null, t)), t);
        }
        for (String t : List.of("Brahma Sub Zero Lata 269cc", "Caixa de Original 269ml pack com 8 unidades",
                "Brahma 120 latas", "Brahma lata 12 ml", "Brahma 2012")) {
            assertFalse(p.nombraCantidad(conPagina("https://a/x.jpg", null, t)), t);
        }
        assertTrue(p.nombraCantidad(conPagina("https://a/x.jpg", "https://tienda.com/brahma-sub-zero-pack-x-12", "Brahma")),
                "también vale la página");
    }

    @Test
    void otroTamanoVaAlFinal() {
        assertEquals("ml:269", BusquedaPack.tamano("BRAHMA SUB ZERO LATA 269 ML PACK X 12"));
        assertEquals("ml:1500", BusquedaPack.tamano("COCA COLA 1,5 L"));
        assertEquals("g:1000", BusquedaPack.tamano("ARROZ 1KG"));
        assertNull(BusquedaPack.tamano("BRAHMA PACK X 12"));

        ImagenSugerida igual = conPagina("https://a/1.jpg", null, "Brahma Sub Zero Lata 269cc");
        ImagenSugerida otro = conPagina("https://a/2.jpg", null, "Cerveja Brahma Zero Lata 350ml Pack 12");
        ImagenSugerida sinTamano = conPagina("https://a/3.jpg", null, "Brahma Sub Zero");
        ImagenSugerida ambos = conPagina("https://a/4.jpg", null, "Brahma 269ml y 350ml");
        assertFalse(BusquedaPack.nombraOtroTamano(igual, "ml:269"));
        assertTrue(BusquedaPack.nombraOtroTamano(otro, "ml:269"));
        assertFalse(BusquedaPack.nombraOtroTamano(sinTamano, "ml:269"));
        assertFalse(BusquedaPack.nombraOtroTamano(ambos, "ml:269"));
        assertFalse(BusquedaPack.nombraOtroTamano(conPagina("https://a/5.jpg", null, "Brahma 1kg"), "ml:269"), "otra medida no cuenta");
        assertEquals(List.of(igual, sinTamano, otro),
                SugerenciaImagenService.ordenarPorTamano(List.of(otro, sinTamano, igual), "ml:269"));
        assertEquals(List.of(otro, sinTamano), SugerenciaImagenService.ordenarPorTamano(List.of(otro, sinTamano), null),
                "si el producto no dice tamaño no se reordena");
    }

    /** Recorte del HTML real de search.brave.com/images?q=7840050007399 (2026-10-15). */
    private static final String BRAVE_IMAGENES = "related_queries:[],bo_altered_diff:[]},results:[{title:\"7840050007399\","
            + "url:\"https://salemmaonline.com.py/bebidas-c-alcohol/bebidas-c-alcohol?sort=PRICE_ASC\",is_source_local:false,"
            + "description:\"\",page_fetched:\"2023-03-10T00:52:16Z\",profile:null,family_friendly:true,bo_debug:{},icons:null,"
            + "source:\"salemmaonline.com.py\",thumbnail:{src:\"https://imgs.search.brave.com/BSru/rs:fit:500:0:1:0/g:ce/aHR0\","
            + "alt:null,height:728,width:316,bg_color:null,original:\"https://salemmaonline.com.py/products/7840050007399.jpg?v=2\","
            + "resized:null,logo:null},properties:{url:\"https://salemmaonline.com.py/products/7840050007399.jpg?v=2\","
            + "resized:\"https://imgs.search.brave.com/QHCl/rs:fit:860:0:0:0/g:ce/aHR0\",placeholder:\"https://imgs.search.brave.com/P4l\","
            + "height:728,width:316,format:null,content_size:null},meta_url:{scheme:\"https\",netloc:\"salemmaonline.com.py\","
            + "hostname:\"salemmaonline.com.py\",favicon:a,path:\"› bebidas\"},from_context:false,confidence:\"medium\","
            + "similar_images_api_url:\"/api/images/similar?q=7840050007399&url=https%3A%2F%2Fsalemmaonline.com.py%2Fproducts%2F7840050007399.jpg"
            + "&safesearch=moderate&nonce=9082&sig=091f\"},"
            + "{title:\"Kodebar\",url:\"https://kodebar.com.br/x\",source:\"kodebar.korvensistemas.com.br\",thumbnail:{src:\"https://imgs.search.brave.com/k\"},"
            + "properties:{url:\"https://kodebar.korvensistemas.com.br/qrcode/7840050007399.svg\",resized:null,height:300,width:300}},"
            + "{title:\"Historial Encriptado y Privado\",url:\"#\"}]";

    /** Recorte del HTML real de search.brave.com/search?q=7840050007399 (2026-10-15). */
    private static final String BRAVE_PAGINAS = "{title:\"Cerveza Pilsen pura malta en lata, 310 ml | cadena real\","
            + "url:\"https://www.realonline.com.py/p/cerveza-pilsen-pura-malta-lata-310-ml-7840050007399\",full_title:void 0,"
            + "description:\"\\u003Cstrong>Cerveza Pilsen pura malta\\u003C/strong> | Pilsen\",profile:{name:\"Realonline\",long_name:\"realonline.com.py\"},"
            + "type:\"search_result\",meta_url:{scheme:\"https\",netloc:\"realonline.com.py\",hostname:\"www.realonline.com.py\",favicon:e,path:\"› p\"},"
            + "thumbnail:{src:\"https://imgs.search.brave.com/r-2b/rs:fit:200:200:1:0/g:ce/aHR0\","
            + "original:\"https://ik.imagekit.io/kfspmjidb/Real-Online/7840050007399.jpg\",logo:false,is_tripadvisor:false},age:void 0},"
            + "{title:\"Cerveza Pilsen pura malta en lata, 310 ml\",url:\"https://www.realonline.com.py/cerveza-pilsen.html\","
            + "thumbnail:{src:\"https://imgs.search.brave.com/iOI9\",original:\"https://www.realonline.com.py/media/catalog/product/placeholder/default/imagen-no-disponible2.png\",logo:false}},"
            + "{title:\"Pilsen\",url:\"https://pilsen.com.py\",thumbnail:{src:\"https://imgs.search.brave.com/l\",original:\"https://pilsen.com.py/logo.png\",logo:true}},"
            + "{title:\"Sin foto\",url:\"https://a.com\",description:\"x\"}";

    @Test
    void braveWebLeeLasImagenesConSuRutaDeParecidas() {
        List<ImagenSugerida> l = BraveWebImagenes.parsearImagenes(BRAVE_IMAGENES);
        assertEquals(1, l.size(), "el QR en .svg y lo que no es resultado se descartan");
        ImagenSugerida a = l.get(0);
        assertEquals("https://salemmaonline.com.py/products/7840050007399.jpg?v=2", a.getUrl());
        assertEquals("https://imgs.search.brave.com/BSru/rs:fit:500:0:1:0/g:ce/aHR0", a.getMiniatura());
        assertEquals(316, a.getAncho());
        assertEquals(728, a.getAlto());
        assertEquals("7840050007399", a.getTitulo());
        assertEquals("salemmaonline.com.py", a.getSitio());
        assertEquals(ImagenSugerida.BRAVE_WEB, a.getFuente());
        assertTrue(a.getSimilares().startsWith("/api/images/similar?q=7840050007399&url="));
        assertTrue(BraveWebImagenes.parsearImagenes("<html>captcha</html>").isEmpty(), "si Brave cambia la página no rompe");
        assertTrue(BraveWebImagenes.parsearImagenes(BRAVE_IMAGENES
                .replace("salemmaonline.com.py/products/7840050007399.jpg?v=2\",resized", "a.com/no-photo-240.png\",resized"))
                .isEmpty(), "la foto de «sin imagen» de una tienda no es el producto");
        assertTrue(BraveWebImagenes.parsearImagenes(null).isEmpty());
    }

    @Test
    void braveWebTomaLaFotoDeCadaPaginaSinLogosNiPlaceholders() {
        List<ImagenSugerida> l = BraveWebImagenes.parsearPaginas(BRAVE_PAGINAS);
        assertEquals(1, l.size());
        ImagenSugerida a = l.get(0);
        assertEquals("https://ik.imagekit.io/kfspmjidb/Real-Online/7840050007399.jpg", a.getUrl());
        assertEquals("https://www.realonline.com.py/p/cerveza-pilsen-pura-malta-lata-310-ml-7840050007399", a.getPagina());
        assertEquals("realonline.com.py", a.getSitio());
        assertNull(a.getAncho());
        assertNull(a.getSimilares());
    }

    @Test
    void braveWebParecidasSoloAceptaLaRutaDeBrave() throws Exception {
        assertTrue(BraveWebImagenes.esRutaParecidas("/api/images/similar?q=1&url=https%3A%2F%2Fa.com%2Fb.jpg&nonce=1&sig=2"));
        assertFalse(BraveWebImagenes.esRutaParecidas("https://evil.com/api/images/similar?"));
        assertFalse(BraveWebImagenes.esRutaParecidas("/api/images/similar?x=1//evil.com"));
        assertFalse(BraveWebImagenes.esRutaParecidas("/api/../admin?"));
        assertFalse(BraveWebImagenes.esRutaParecidas(null));
        List<ImagenSugerida> l = BraveWebImagenes.parsearParecidas(json("{'type':'images','results':[{'title':'7840050007498',"
                + "'url':'https://salemmaonline.com.py/x','source':'salemmaonline.com.py','thumbnail':{'src':'https://imgs.search.brave.com/t'},"
                + "'properties':{'url':'https://salemmaonline.com.py/products/7840050007498.png?v=2','height':700,'width':500},"
                + "'similar_images_api_url':'/api/images/similar?q=1&sig=3'},"
                + "{'title':'qr','properties':{'url':'https://a.com/qrcode/1.svg'}}]}"));
        assertEquals(1, l.size());
        assertEquals(500, l.get(0).getAncho());
        assertEquals("/api/images/similar?q=1&sig=3", l.get(0).getSimilares());
    }

    @Test
    void porDescripcionSubenLasQueNombranLasPalabrasDistintivas() {
        // Títulos reales de Bing para «DON FRANCO IPA HAPEE» (2026-10-15): corrige «hapee» a «happy».
        ImagenSugerida worry = conPagina("https://lamundial.es/1.jpg", null, "STIGBERGETS – DON’T WORRY BE HAPPY – IPA");
        ImagenSugerida hop = conPagina("https://hopsmash.com/2.jpg", null, "Milwaukee – Hop Happy IPA – Hopsmash");
        ImagenSugerida greek = conPagina("https://greekbeershop.gr/3.jpg", null, "Happy Brewers East Coast IPA");
        ImagenSugerida donFranco = conPagina("https://lookaside.fbsbx.com/4.jpg", null, "Don Franco Cervecería Artesanal | Salto del Guairá");
        ImagenSugerida camper = conPagina("https://happytammsvik.se/5.jpg", null, "Happy Camper - Vår egen Hazy IPA");
        List<ImagenSugerida> l = RelevanciaTexto.ordenar(List.of(worry, hop, greek, donFranco, camper), "DON FRANCO IPA HAPEE", null);
        assertEquals(donFranco, l.get(0), "«ipa» está en todos y no distingue; «don franco» sí");
        assertEquals(List.of(worry, hop, greek, camper), l.subList(1, 5), "el resto, en el orden que traía («don’t» también suma «don»)");

        List<ImagenSugerida> pocas = List.of(hop, donFranco);
        assertEquals(pocas, RelevanciaTexto.ordenar(pocas, "DON FRANCO IPA HAPEE", null), "con pocos resultados no se reordena");
        assertEquals(Set.of("brahma", "sub", "zero", "lata", "269"), RelevanciaTexto.palabras("BRAHMA SUB ZERO LATA 269 ML PACK X 12 UNIDAD"));
    }

    @Test
    void parecidasDeUnaFotoDeBingBuscanLaMismaFotoEnBrave() {
        ImagenSugerida otra = conPagina("https://assets.untappd.com/photo/2021_05_09/otra.jpg", null, "Check-in");
        otra.setSimilares("/api/images/similar?q=a&sig=1");
        ImagenSugerida mismaArchivo = conPagina("https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg?w=300", null, "Brahma");
        mismaArchivo.setSimilares("/api/images/similar?q=b&sig=2");
        ImagenSugerida sinSimilares = conPagina("https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg", null, "Brahma");
        assertEquals(mismaArchivo, SugerenciaImagenService.mismaFoto(List.of(sinSimilares, otra, mismaArchivo),
                "https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg"), "mismo archivo del mismo sitio, con parecidas");
        assertNull(SugerenciaImagenService.mismaFoto(List.of(otra), "https://assets.untappd.com/photos/2021_04_15/b_640x640.jpg"));
        assertNull(SugerenciaImagenService.mismaFoto(List.of(mismaArchivo), "https://otro.com/photos/b_640x640.jpg"),
                "el mismo nombre de archivo en otro sitio no es la misma foto");
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
