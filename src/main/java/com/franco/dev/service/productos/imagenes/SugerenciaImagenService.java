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
 *       primero (es el producto exacto) y después el código como texto en el buscador.</li>
 *   <li><b>Por descripción</b>: producto + presentación, tal cual (una promo trae fotos de promos
 *       si las hay, y si no, del producto).</li>
 * </ul>
 * Las fuentes corren en paralelo y una que falla no tumba a las otras: queda en {@code avisos}.
 * Brave se usa solo de respaldo de SearXNG, porque cada búsqueda consume crédito.
 */
@Service
public class SugerenciaImagenService {

    private static final Logger log = LoggerFactory.getLogger(SugerenciaImagenService.class);

    private static final int MAX_POR_CODIGO = 20;
    private static final int MAX_POR_DESCRIPCION = 40;
    /**
     * Por código el resultado suele ser el producto exacto en una tienda online, muchas veces a 300 px:
     * ahí pesa más que sea el producto que la resolución.
     */
    private static final int LADO_MINIMO_POR_CODIGO = 300;
    /** Lado menor / lado mayor desde el que una foto cuenta como "casi cuadrada". */
    private static final double PROPORCION_CUADRADA = 0.6;

    private final PresentacionService presentacionService;
    private final CodigoService codigoService;
    private final OpenFoodFactsImagenes openFoodFacts;
    private final SearxngImagenes searxng;
    private final BraveImagenes brave;
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
                                   SearxngImagenes searxng,
                                   BraveImagenes brave,
                                   DescargaImagenService descarga,
                                   @Value("${imagenes.sugerencias.lado-minimo:500}") int ladoMinimo,
                                   @Value("${imagenes.sugerencias.timeout-ms:8000}") long timeoutMs) {
        this.presentacionService = presentacionService;
        this.codigoService = codigoService;
        this.openFoodFacts = openFoodFacts;
        this.searxng = searxng;
        this.brave = brave;
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
        r.setTexto(texto != null && !texto.isBlank() ? texto.trim().replaceAll("\\s+", " ") : textoSugerido(presentacion));

        List<String> avisos = Collections.synchronizedList(new ArrayList<>());
        boolean hayBuscador = searxng.habilitada() || brave.habilitada();
        if (!hayBuscador) {
            avisos.add("No hay buscador de imágenes configurado en el servidor (SearXNG o Brave): solo se busca por código en Open Food Facts.");
        }

        CompletableFuture<List<ImagenSugerida>> off = r.getCodigo() != null && openFoodFacts.habilitada()
                ? enParalelo("Open Food Facts", avisos, () -> openFoodFacts.buscar(r.getCodigo())) : vacio();
        CompletableFuture<List<ImagenSugerida>> webCodigo = r.getCodigo() != null
                ? buscarEnLaWeb(r.getCodigo(), avisos) : vacio();
        CompletableFuture<List<ImagenSugerida>> webTexto = !r.getTexto().isEmpty()
                ? buscarEnLaWeb(r.getTexto(), avisos) : vacio();
        CompletableFuture.allOf(off, webCodigo, webTexto).join();

        Set<String> vistas = new HashSet<>();
        List<ImagenSugerida> porCodigo = new ArrayList<>(ordenar(off.join(), vistas, 0, MAX_POR_CODIGO));
        porCodigo.addAll(ordenar(webCodigo.join(), vistas, Math.min(ladoMinimo, LADO_MINIMO_POR_CODIGO), MAX_POR_CODIGO - porCodigo.size()));
        r.setPorCodigo(porCodigo);
        r.setPorDescripcion(ordenar(webTexto.join(), vistas, ladoMinimo, MAX_POR_DESCRIPCION));
        r.setAvisos(new ArrayList<>(avisos));

        List<String> emitidas = new ArrayList<>();
        for (List<ImagenSugerida> l : List.of(r.getPorCodigo(), r.getPorDescripcion())) {
            for (ImagenSugerida i : l) {
                emitidas.add(i.getUrl());
                emitidas.add(i.getMiniatura());
            }
        }
        descarga.registrar(emitidas);
        return r;
    }

    /** SearXNG, y Brave si SearXNG no está, falla o no trae nada. */
    private CompletableFuture<List<ImagenSugerida>> buscarEnLaWeb(String q, List<String> avisos) {
        CompletableFuture<List<ImagenSugerida>> primero = searxng.habilitada()
                ? enParalelo("SearXNG", avisos, () -> searxng.buscar(q)) : vacio();
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

    private static String clave(String url) {
        return url.trim().replaceFirst("^http://", "https://").replaceFirst("#.*$", "");
    }
}
