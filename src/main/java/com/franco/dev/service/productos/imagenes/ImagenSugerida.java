package com.franco.dev.service.productos.imagenes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una foto encontrada en internet para una presentación. {@code ancho}/{@code alto} son null
 * cuando la fuente no informa el tamaño.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImagenSugerida {

    public static final String OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS";
    public static final String BING = "BING";
    public static final String BRAVE = "BRAVE";

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
}
