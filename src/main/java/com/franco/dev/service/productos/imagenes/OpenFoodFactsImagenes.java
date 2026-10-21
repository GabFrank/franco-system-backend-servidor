package com.franco.dev.service.productos.imagenes;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Open Food Facts: base abierta de productos por código de barras, gratis y sin key. Si el código
 * está cargado, la foto es la del producto exacto (casi siempre la unidad, no el pack).
 */
@Component
public class OpenFoodFactsImagenes {

    private static final String API = "https://world.openfoodfacts.org/api/v2/product/";
    /** Open Food Facts pide identificarse con un User-Agent propio. */
    private static final Map<String, String> HEADERS = Map.of("User-Agent", "FrcSistemas/3.0 (sugerencia de fotos de productos)");

    private final ClienteHttpImagenes cliente;
    private final boolean habilitada;

    public OpenFoodFactsImagenes(ClienteHttpImagenes cliente,
                                 @Value("${imagenes.sugerencias.open-food-facts.enabled:true}") boolean habilitada) {
        this.cliente = cliente;
        this.habilitada = habilitada;
    }

    public boolean habilitada() {
        return habilitada;
    }

    public List<ImagenSugerida> buscar(String codigo) throws IOException {
        String url = API + ClienteHttpImagenes.enc(codigo) + ".json?fields=product_name,brands,image_front_url,image_url";
        return parsear(cliente.get(url, HEADERS));
    }

    static List<ImagenSugerida> parsear(JsonNode raiz) {
        List<ImagenSugerida> lista = new ArrayList<>();
        if (raiz == null || raiz.path("status").asInt(0) != 1) return lista;
        JsonNode p = raiz.path("product");
        String foto = ClienteHttpImagenes.texto(p, "image_front_url");
        if (foto == null) foto = ClienteHttpImagenes.texto(p, "image_url");
        if (!ClienteHttpImagenes.esHttp(foto)) return lista;
        String titulo = ClienteHttpImagenes.texto(p, "product_name");
        String marca = ClienteHttpImagenes.texto(p, "brands");
        if (marca != null) titulo = titulo == null ? marca : marca + " - " + titulo;
        // Las URLs terminan en ".400.jpg"; ".full.jpg" es el original subido.
        String completa = foto.replaceFirst("\\.\\d+\\.jpg$", ".full.jpg");
        String pagina = "https://world.openfoodfacts.org/product/" + ClienteHttpImagenes.texto(raiz, "code");
        lista.add(new ImagenSugerida(completa, foto, null, null, titulo, "openfoodfacts.org", pagina, ImagenSugerida.OPEN_FOOD_FACTS));
        return lista;
    }
}
