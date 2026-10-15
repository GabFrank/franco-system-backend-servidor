package com.franco.dev.service.productos.imagenes;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.utilitarios.Ean13Utils;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Busca en internet fotos para una presentación, por su código de barras y por su descripción.
 *
 * <ul>
 *   <li><b>Por código</b>: solo si es un GTIN comercial ({@link Ean13Utils#esCodigoComercial});
 *       los internos (2199…, balanza, códigos sueltos) no existen fuera del local. Open Food Facts
 *       y Brave (imágenes y páginas, {@link BraveWebImagenes}), que con el número solo encuentra el
 *       producto exacto; Bing solo de respaldo, porque busca mal los números. Primero las exactas:
 *       las que nombran el código.</li>
 *   <li><b>Por descripción</b>: producto + presentación, tal cual (una promo trae fotos de promos
 *       si las hay, y si no, del producto). Brave y Bing a la vez: las de Brave primero, porque
 *       admiten «parecidas» visual; después, ordenadas por las palabras distintivas que nombran
 *       ({@link RelevanciaTexto}).</li>
 *   <li><b>Pack</b>: si es de varias unidades ({@link BusquedaPack}), además una consulta corta
 *       «producto pack 12»; las fotos que nombran la cantidad van en su propia lista.</li>
 * </ul>
 * En pack y descripción, primero las fotos que nombran el tamaño del producto y al final las de otro
 * tamaño (350 ml cuando el producto es de 269 ml).
 * Todo corre dentro del central, sin nada instalado aparte. Las fuentes corren en paralelo y una
 * que falla no tumba a las otras: queda en {@code avisos}. Brave (opcional, con key) se usa solo de
 * respaldo de Bing, porque cada búsqueda consume crédito.
 */
@Service
public class SugerenciaImagenService {

    private static final Logger log = LoggerFactory.getLogger(SugerenciaImagenService.class);

    private static final int MAX_POR_CODIGO = 20;
    private static final int MAX_POR_DESCRIPCION = 40;
    private static final int MAX_POR_PACK = 20;
    /** Fotos de un pack puntual hay pocas y suelen ser chicas: como por código, pesa más que sea el producto. */
    private static final int LADO_MINIMO_POR_PACK = 150;
    /**
     * Por código el resultado suele ser el producto exacto en una tienda online, muchas veces a 300 px:
     * ahí pesa más que sea el producto que la resolución.
     */
    private static final int LADO_MINIMO_POR_CODIGO = 300;
    /**
     * Con un número suelto el primer resultado suele ser el producto y el resto es ruido (en Brave,
     * otros productos de la misma tienda). Por código se muestran los que nombran el código y, del
     * resto, solo estos primeros.
     */
    private static final int PRIMEROS_POR_CODIGO = 3;
    /** Una foto exacta sirve aunque sea chica; por debajo de esto es un ícono. */
    private static final int LADO_MINIMO_EXACTA = 100;
    /** Lado menor / lado mayor desde el que una foto cuenta como "casi cuadrada". */
    private static final double PROPORCION_CUADRADA = 0.6;

    private final PresentacionService presentacionService;
    private final CodigoService codigoService;
    private final OpenFoodFactsImagenes openFoodFacts;
    private final BingImagenes bing;
    private final BraveImagenes brave;
    private final BraveWebImagenes braveWeb;
    private final DescargaImagenService descarga;
    private final int ladoMinimo;
    private final long timeoutMs;

    /** Hilos daemon propios: una fuente colgada en DNS no frena a la JVM ni a las otras fuentes. */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sugerencia-imagen");
        t.setDaemon(true);
        return t;
    });

    public SugerenciaImagenService(PresentacionService presentacionService,
                                   CodigoService codigoService,
                                   OpenFoodFactsImagenes openFoodFacts,
                                   BingImagenes bing,
                                   BraveImagenes brave,
                                   BraveWebImagenes braveWeb,
                                   DescargaImagenService descarga,
                                   @Value("${imagenes.sugerencias.lado-minimo:500}") int ladoMinimo,
                                   @Value("${imagenes.sugerencias.timeout-ms:8000}") long timeoutMs) {
        this.presentacionService = presentacionService;
        this.codigoService = codigoService;
        this.openFoodFacts = openFoodFacts;
        this.bing = bing;
        this.brave = brave;
        this.braveWeb = braveWeb;
        this.descarga = descarga;
        this.ladoMinimo = ladoMinimo;
        this.timeoutMs = timeoutMs;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * @param codigo null = el de la presentación; vacío = no buscar por código.
     * @param texto  null o vacío = producto + presentación.
     */
    public SugerenciasImagen sugerir(Long presentacionId, String codigo, String texto) {
        Presentacion presentacion = presentacionService.findById(presentacionId)
                .orElseThrow(() -> new GraphQLException("No existe la presentación " + presentacionId));

        SugerenciasImagen r = new SugerenciasImagen();
        String cod = codigo != null ? codigo.trim() : codigoDeLaPresentacion(presentacionId);
        if (cod != null && !cod.isEmpty()) {
            if (Ean13Utils.esCodigoComercial(cod)) r.setCodigo(cod);
            else r.setCodigoDescartado(cod);
        }
        boolean textoDelUsuario = texto != null && !texto.isBlank();
        r.setTexto(textoDelUsuario ? texto.trim().replaceAll("\\s+", " ") : textoSugerido(presentacion));
        BusquedaPack pack = BusquedaPack.detectar(r.getTexto(), presentacion.getCantidad(), textoDelUsuario);
        String tamano = BusquedaPack.tamano(r.getTexto());
        if (pack != null) r.setPack(pack.palabra + " x " + pack.cantidad);

        List<String> avisos = Collections.synchronizedList(new ArrayList<>());
        if (!bing.habilitada() && !brave.habilitada() && !braveWeb.habilitada()) {
            avisos.add("La búsqueda en internet está apagada en el servidor: solo se busca por código en Open Food Facts.");
        }

        CompletableFuture<List<ImagenSugerida>> off = r.getCodigo() != null && openFoodFacts.habilitada()
                ? enParalelo("Open Food Facts", avisos, () -> openFoodFacts.buscar(r.getCodigo())) : vacio();
        CompletableFuture<List<ImagenSugerida>> webCodigo = r.getCodigo() != null
                ? buscarPorCodigo(r.getCodigo(), r.getTexto(), avisos) : vacio();
        CompletableFuture<List<ImagenSugerida>> webTexto = !r.getTexto().isEmpty()
                ? buscarEnLaWeb(r.getTexto(), avisos) : vacio();
        CompletableFuture<List<ImagenSugerida>> braveTexto = !r.getTexto().isEmpty() && braveWeb.habilitada()
                ? enParalelo("Brave", avisos, () -> sinAviso(() -> braveWeb.imagenes(r.getTexto()), "imágenes por descripción"))
                : vacio();
        CompletableFuture<List<ImagenSugerida>> webPack = pack != null
                ? buscarEnLaWeb(pack.consulta(r.getTexto()), avisos) : vacio();
        CompletableFuture.allOf(off, webCodigo, webTexto, braveTexto, webPack).join();

        Set<String> vistas = new HashSet<>();
        List<ImagenSugerida> porCodigo = new ArrayList<>(ordenar(off.join(), vistas, 0, MAX_POR_CODIGO));
        porCodigo.forEach(i -> i.setExacta(true));
        porCodigo.addAll(filtrarPorCodigo(webCodigo.join(), r.getCodigo(), vistas,
                Math.min(ladoMinimo, LADO_MINIMO_POR_CODIGO), MAX_POR_CODIGO - porCodigo.size()));
        r.setPorCodigo(porCodigo);

        List<ImagenSugerida> descripcion = new ArrayList<>(braveTexto.join());
        descripcion.addAll(webTexto.join());
        if (pack != null) {
            List<ImagenSugerida> candidatas = new ArrayList<>(webPack.join());
            candidatas.addAll(descripcion);
            List<ImagenSugerida> delPack = new ArrayList<>();
            for (ImagenSugerida i : candidatas) {
                if (pack.nombraCantidad(i) && BusquedaPack.nombraMarca(i, r.getTexto())) delPack.add(i);
            }
            r.setPorPack(primero(ordenarPorTamano(ordenar(delPack, vistas, Math.min(ladoMinimo, LADO_MINIMO_POR_PACK),
                    Integer.MAX_VALUE), tamano), MAX_POR_PACK));
            // Lo de la consulta corta que no nombra la cantidad puede ser igual el producto: después de lo de la descripción.
            descripcion.addAll(webPack.join());
        }
        r.setPorDescripcion(primero(RelevanciaTexto.ordenar(ordenarPorFuente(descripcion, vistas), r.getTexto(), tamano),
                MAX_POR_DESCRIPCION));
        r.setAvisos(new ArrayList<>(avisos));

        registrar(r.getPorCodigo());
        registrar(r.getPorPack());
        registrar(r.getPorDescripcion());
        return r;
    }

    /**
     * Fotos parecidas a una sugerida, para que siempre haya resultados:
     * <ol>
     *   <li><b>Brave visual</b>: con su {@code similares}, o si no lo trae (vino de Bing u Open Food Facts),
     *       ubicándola en Brave por su título; si no está, las parecidas a la primera foto que trae Brave
     *       ({@code aproximada}).</li>
     *   <li>Si Brave no responde (429, caído) o no trae nada: <b>Bing</b> con el título de la foto, sin
     *       ella misma y ordenadas por las palabras distintivas del título ({@code porTexto}). La búsqueda
     *       visual de Bing pide key (probado el 2026-10-15): sin ella devuelve vacío.</li>
     * </ol>
     *
     * @param texto      el título de la foto ya limpio (sin el sitio); si no trae, lo que se buscó.
     * @param similares  la ruta de parecidas de Brave, si la foto vino de Brave.
     */
    public ParecidasImagen parecidasDe(String url, String texto, String similares) {
        String t = texto == null ? "" : texto.trim();
        if (braveWeb.habilitada()) {
            try {
                ParecidasImagen res = parecidasEnBrave(url, t, similares);
                if (res != null && !res.getImagenes().isEmpty()) return res;
            } catch (IOException | RuntimeException e) {
                log.info("Sugerencia de imágenes: parecidas de {} en Brave falló, se busca en Bing: {}", url, e.toString());
            }
        }
        if (t.isEmpty() || !bing.habilitada()) {
            throw new GraphQLException("No se encontraron fotos parecidas a esa. Probá con otra.");
        }
        List<ImagenSugerida> l;
        try {
            l = bing.buscar(t, 0);
        } catch (IOException | RuntimeException e) {
            log.info("Sugerencia de imágenes: parecidas de {} en Bing falló: {}", url, e.toString());
            throw new GraphQLException("No se pudieron buscar parecidas: ni Brave ni Bing respondieron. Revisá la conexión del servidor.");
        }
        Set<String> vistas = new HashSet<>();
        if (url != null) vistas.add(clave(url));
        ParecidasImagen res = new ParecidasImagen();
        res.setPorTexto(true);
        res.setImagenes(primero(RelevanciaTexto.ordenar(ordenar(l, vistas, ladoMinimo, Integer.MAX_VALUE), t,
                BusquedaPack.tamano(t)), MAX_POR_DESCRIPCION));
        registrar(res.getImagenes());
        return res;
    }

    /** null si no hay con qué buscar en Brave (sin {@code similares} ni título). */
    private ParecidasImagen parecidasEnBrave(String url, String texto, String similares) throws IOException {
        String ruta = BraveWebImagenes.esRutaParecidas(similares) ? similares : null;
        boolean aproximada = false;
        if (ruta == null) {
            if (texto.isEmpty()) return null;
            List<ImagenSugerida> candidatas = braveWeb.imagenes(texto);
            ImagenSugerida base = mismaFoto(candidatas, url);
            aproximada = base == null;
            if (base == null) {
                for (ImagenSugerida i : candidatas) {
                    if (i.getSimilares() != null) {
                        base = i;
                        break;
                    }
                }
            }
            if (base == null) return null;
            ruta = base.getSimilares();
        }
        Set<String> vistas = new HashSet<>();
        if (url != null) vistas.add(clave(url));
        ParecidasImagen res = new ParecidasImagen();
        res.setAproximada(aproximada);
        res.setImagenes(ordenar(braveWeb.parecidas(ruta), vistas, Math.min(ladoMinimo, LADO_MINIMO_POR_CODIGO), MAX_POR_DESCRIPCION));
        registrar(res.getImagenes());
        return res;
    }

    /** La misma foto: misma URL, o el mismo archivo en el mismo sitio (cambian los parámetros de tamaño). */
    static ImagenSugerida mismaFoto(List<ImagenSugerida> candidatas, String url) {
        String buscada = clave(url);
        String archivo = archivo(url);
        String dominio = ClienteHttpImagenes.dominio(url);
        ImagenSugerida porArchivo = null;
        for (ImagenSugerida i : candidatas) {
            if (i.getSimilares() == null) continue;
            if (clave(i.getUrl()).equals(buscada)) return i;
            if (porArchivo == null && archivo != null && archivo.equals(archivo(i.getUrl()))
                    && dominio != null && dominio.equals(ClienteHttpImagenes.dominio(i.getUrl()))) {
                porArchivo = i;
            }
        }
        return porArchivo;
    }

    private static String archivo(String url) {
        if (url == null) return null;
        String sinQuery = url.replaceFirst("[?#].*$", "");
        String a = sinQuery.substring(sinQuery.lastIndexOf('/') + 1);
        return a.length() >= 6 ? a : null;
    }

    /** Lo de Brave primero (admite parecidas visual), después lo de Bing; cada grupo como {@link #ordenar}. */
    private List<ImagenSugerida> ordenarPorFuente(List<ImagenSugerida> imagenes, Set<String> vistas) {
        List<ImagenSugerida> deBrave = new ArrayList<>();
        List<ImagenSugerida> otras = new ArrayList<>();
        for (ImagenSugerida i : imagenes) (ImagenSugerida.BRAVE_WEB.equals(i.getFuente()) ? deBrave : otras).add(i);
        List<ImagenSugerida> res = new ArrayList<>(ordenar(deBrave, vistas, ladoMinimo, Integer.MAX_VALUE));
        res.addAll(ordenar(otras, vistas, ladoMinimo, Integer.MAX_VALUE));
        return res;
    }

    /** Una fuente que suma pero no es la principal: si falla se loguea y no se avisa en el panel. */
    private static List<ImagenSugerida> sinAviso(Callable<List<ImagenSugerida>> busqueda, String que) {
        try {
            return busqueda.call();
        } catch (Exception e) {
            log.info("Sugerencia de imágenes: {} de Brave falló: {}", que, e.toString());
            return Collections.emptyList();
        }
    }

    /** Solo se deja bajar lo que salió de una búsqueda: ver {@link DescargaImagenService}. */
    private void registrar(List<ImagenSugerida> imagenes) {
        List<String> emitidas = new ArrayList<>();
        for (ImagenSugerida i : imagenes) {
            emitidas.add(i.getUrl());
            emitidas.add(i.getMiniatura());
        }
        descarga.registrar(emitidas);
    }

    /**
     * Brave con el código solo: imágenes y páginas a la vez. Si Brave está apagado, falla o no trae
     * nada, Bing con «código + marca» ({@link #consultaPorCodigo}).
     */
    private CompletableFuture<List<ImagenSugerida>> buscarPorCodigo(String codigo, String texto, List<String> avisos) {
        CompletableFuture<List<ImagenSugerida>> primero = vacio();
        if (braveWeb.habilitada()) {
            CompletableFuture<List<ImagenSugerida>> imagenes = enParalelo("Brave", avisos, () -> braveWeb.imagenes(codigo));
            // Las páginas suman, pero Brave las limita más (429) que las imágenes: si fallan no se avisa.
            CompletableFuture<List<ImagenSugerida>> paginas = enParalelo("Brave", avisos,
                    () -> sinAviso(() -> braveWeb.paginas(codigo), "páginas por código"));
            primero = imagenes.thenCombine(paginas, (a, b) -> {
                List<ImagenSugerida> todas = new ArrayList<>(a);
                todas.addAll(b);
                return todas;
            });
        }
        return primero.thenCompose(l -> l.isEmpty()
                ? buscarEnLaWeb(consultaPorCodigo(codigo, texto), avisos)
                : CompletableFuture.completedFuture(l));
    }

    /** Dos páginas de Bing a la vez (~70 fotos), y Brave si Bing no está, falla o no trae nada. */
    private CompletableFuture<List<ImagenSugerida>> buscarEnLaWeb(String q, List<String> avisos) {
        CompletableFuture<List<ImagenSugerida>> primero = vacio();
        if (bing.habilitada()) {
            CompletableFuture<List<ImagenSugerida>> p1 = enParalelo("Bing", avisos, () -> bing.buscar(q, 0));
            CompletableFuture<List<ImagenSugerida>> p2 = enParalelo("Bing", avisos, () -> bing.buscar(q, 1));
            primero = p1.thenCombine(p2, (a, b) -> {
                List<ImagenSugerida> todas = new ArrayList<>(a);
                todas.addAll(b);
                return todas;
            });
        }
        return primero.thenCompose(l -> {
            if (!l.isEmpty() || !brave.habilitada()) return CompletableFuture.completedFuture(l);
            return enParalelo("Brave", avisos, () -> brave.buscar(q));
        });
    }

    private CompletableFuture<List<ImagenSugerida>> enParalelo(String fuente, List<String> avisos,
                                                              Callable<List<ImagenSugerida>> busqueda) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return busqueda.call();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, executor)
                .orTimeout(timeoutMs + 2000, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.info("Sugerencia de imágenes: {} falló: {}", fuente, e.toString());
                    String aviso = fuente + " no respondió.";
                    if (!avisos.contains(aviso)) avisos.add(aviso);
                    return Collections.emptyList();
                });
    }

    private static CompletableFuture<List<ImagenSugerida>> vacio() {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * El primer código comercial activo de la presentación, el principal antes. Si no tiene
     * ninguno, el principal igual, para que el desktop muestre que es interno y no se buscó.
     */
    private String codigoDeLaPresentacion(Long presentacionId) {
        List<Codigo> codigos = new ArrayList<>(codigoService.findByPresentacionId(presentacionId));
        codigos.removeIf(c -> c.getCodigo() == null || Boolean.FALSE.equals(c.getActivo()));
        codigos.sort(Comparator.comparing((Codigo c) -> !Boolean.TRUE.equals(c.getPrincipal())));
        for (Codigo c : codigos) {
            if (Ean13Utils.esCodigoComercial(c.getCodigo())) return c.getCodigo().trim();
        }
        return codigos.isEmpty() ? null : codigos.get(0).getCodigo().trim();
    }

    /** Producto + presentación. "UNIDAD" no suma nada: el producto ya es la unidad. */
    static String textoSugerido(Presentacion p) {
        String producto = p.getProducto() != null && p.getProducto().getDescripcion() != null
                ? p.getProducto().getDescripcion().trim() : "";
        String pres = p.getDescripcion() != null ? p.getDescripcion().trim() : "";
        boolean sumaPresentacion = !pres.isEmpty() && !pres.equalsIgnoreCase("UNIDAD")
                && !producto.toUpperCase().contains(pres.toUpperCase());
        return (sumaPresentacion ? producto + " " + pres : producto).trim().replaceAll("\\s+", " ");
    }

    /**
     * El código con la primera palabra de la descripción (casi siempre la marca): «7840050002561 BRAHMA».
     * Con el número solo, Bing mezcla productos de cualquier rubro.
     */
    static String consultaPorCodigo(String codigo, String texto) {
        String primera = texto == null ? "" : texto.trim().split("\\s+")[0];
        return primera.isEmpty() ? codigo : codigo + " " + primera;
    }

    /**
     * Resultados de buscar por código, en el orden del buscador (acá importa más que sea el producto que
     * la forma de la foto):
     * <ol>
     *   <li><b>Exactas</b>: la foto o su título nombran el código. Las tiendas nombran la foto con el
     *       código ({@code /products/7840050007399.jpg}).</li>
     *   <li>De una <b>página que nombra el código</b>, solo la primera foto: el resto de la página suelen
     *       ser productos relacionados, logos, el vendedor.</li>
     *   <li>Del resto, los {@value #PRIMEROS_POR_CODIGO} primeros, solo si no hay ninguna exacta: con
     *       el producto encontrado, el resto es ruido (otros productos de la tienda, videos).</li>
     * </ol>
     * Las dos primeras se aceptan chicas (salvo íconos de menos de {@value #LADO_MINIMO_EXACTA} px); el
     * resto, desde {@code ladoMinimo}.
     */
    static List<ImagenSugerida> filtrarPorCodigo(List<ImagenSugerida> imagenes, String codigo, Set<String> vistas,
                                                int ladoMinimo, int max) {
        List<ImagenSugerida> exactas = new ArrayList<>();
        List<ImagenSugerida> dePagina = new ArrayList<>();
        List<ImagenSugerida> resto = new ArrayList<>();
        Set<String> paginas = new HashSet<>();
        for (ImagenSugerida i : imagenes) {
            if (vistas.contains(clave(i.getUrl()))) continue;
            boolean exacta = i.getUrl().contains(codigo) || (i.getTitulo() != null && i.getTitulo().contains(codigo));
            boolean pagina = !exacta && i.getPagina() != null && i.getPagina().contains(codigo);
            Integer menor = i.getAncho() != null && i.getAlto() != null ? Math.min(i.getAncho(), i.getAlto()) : null;
            if (menor != null && menor < (exacta || pagina ? LADO_MINIMO_EXACTA : ladoMinimo)) continue;
            if (exacta) exactas.add(i);
            else if (pagina) {
                if (paginas.add(i.getPagina())) dePagina.add(i);
            } else resto.add(i);
        }
        List<ImagenSugerida> res = new ArrayList<>();
        for (ImagenSugerida i : exactas) {
            if (res.size() < max && vistas.add(clave(i.getUrl()))) {
                i.setExacta(true);
                res.add(i);
            }
        }
        for (ImagenSugerida i : dePagina) {
            if (res.size() < max && vistas.add(clave(i.getUrl()))) res.add(i);
        }
        int delResto = exactas.isEmpty() ? 0 : PRIMEROS_POR_CODIGO;
        for (ImagenSugerida i : resto) {
            if (res.size() >= max || delResto >= PRIMEROS_POR_CODIGO) break;
            if (vistas.add(clave(i.getUrl()))) {
                res.add(i);
                delResto++;
            }
        }
        return res;
    }

    /**
     * Saca repetidas (también contra {@code vistas}, que se va llenando) y las de tamaño conocido
     * con el lado menor por debajo de {@code ladoMinimo}. Primero las casi cuadradas, que se ven
     * mejor en el POS; después las sin tamaño; al final las alargadas. Dentro de cada grupo se
     * respeta el orden del buscador, que es el de relevancia.
     */
    static List<ImagenSugerida> ordenar(List<ImagenSugerida> imagenes, Set<String> vistas, int ladoMinimo, int max) {
        List<ImagenSugerida> cuadradas = new ArrayList<>();
        List<ImagenSugerida> sinTamano = new ArrayList<>();
        List<ImagenSugerida> alargadas = new ArrayList<>();
        for (ImagenSugerida i : imagenes) {
            if (!vistas.add(clave(i.getUrl()))) continue;
            if (i.getAncho() == null || i.getAlto() == null) {
                sinTamano.add(i);
                continue;
            }
            int menor = Math.min(i.getAncho(), i.getAlto());
            int mayor = Math.max(i.getAncho(), i.getAlto());
            if (menor < ladoMinimo) continue;
            if ((double) menor / mayor >= PROPORCION_CUADRADA) cuadradas.add(i);
            else alargadas.add(i);
        }
        List<ImagenSugerida> res = new ArrayList<>(cuadradas);
        res.addAll(sinTamano);
        res.addAll(alargadas);
        return res.size() > Math.max(max, 0) ? new ArrayList<>(res.subList(0, Math.max(max, 0))) : res;
    }

    /**
     * Primero las que nombran el tamaño del producto, después las que no dicen tamaño y al final las de
     * otro tamaño. Dentro de cada grupo, el mismo orden.
     */
    static List<ImagenSugerida> ordenarPorTamano(List<ImagenSugerida> imagenes, String tamano) {
        if (tamano == null) return imagenes;
        List<ImagenSugerida> mismo = new ArrayList<>();
        List<ImagenSugerida> sinTamano = new ArrayList<>();
        List<ImagenSugerida> otro = new ArrayList<>();
        for (ImagenSugerida i : imagenes) {
            if (BusquedaPack.nombraTamano(i, tamano)) mismo.add(i);
            else if (BusquedaPack.nombraOtroTamano(i, tamano)) otro.add(i);
            else sinTamano.add(i);
        }
        mismo.addAll(sinTamano);
        mismo.addAll(otro);
        return mismo;
    }

    private static List<ImagenSugerida> primero(List<ImagenSugerida> l, int max) {
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    private static String clave(String url) {
        return url.trim().replaceFirst("^http://", "https://").replaceFirst("#.*$", "");
    }
}
