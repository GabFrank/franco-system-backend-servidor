package com.franco.dev.service.productos.imagenes;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una foto encontrada en internet para una presentación. {@code ancho}/{@code alto} son null
 * cuando la fuente no informa el tamaño.
 */
@Data
@NoArgsConstructor
public class ImagenSugerida {

    public static final String OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS";
    public static final String BING = "BING";
    public static final String BRAVE = "BRAVE";
    /** Brave Search leído directo, sin key ({@link BraveWebImagenes}). */
    public static final String BRAVE_WEB = "BRAVE_WEB";

    /** Imagen en tamaño completo: es la que se descarga al elegirla. */
    private String url;
    /** Versión chica para la grilla. Si la fuente no trae una, es la misma {@code url}. */
    private String miniatura;
    private Integer ancho;
    private Integer alto;
    private String titulo;
    /** Dominio de la página donde está la foto. */
    private String sitio;
    /** Página donde está la foto. */
    private String pagina;
    private String fuente;
    /** Es el producto del código buscado: lo confirma Open Food Facts o la foto/página nombra el código. */
    private Boolean exacta;
    /** Solo de Brave: ruta firmada para pedir fotos visualmente parecidas ({@code imagenesParecidasDe}). */
    private String similares;

    public ImagenSugerida(String url, String miniatura, Integer ancho, Integer alto, String titulo, String sitio,
                          String pagina, String fuente) {
        this.url = url;
        this.miniatura = miniatura;
        this.ancho = ancho;
        this.alto = alto;
        this.titulo = titulo;
        this.sitio = sitio;
        this.pagina = pagina;
        this.fuente = fuente;
    }
}
