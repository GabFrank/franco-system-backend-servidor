package com.franco.dev.service.productos.imagenes;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Fotos parecidas a una sugerida ({@link SugerenciaImagenService#parecidasDe}). */
@Data
public class ParecidasImagen {

    private List<ImagenSugerida> imagenes = new ArrayList<>();
    /**
     * La foto elegida no está en Brave: son las parecidas a la primera foto que trae Brave buscando su
     * título, no a ella misma.
     */
    private boolean aproximada;
    /** Brave no respondió o no trajo nada: son de Bing buscando el título de la foto, no parecidas visuales. */
    private boolean porTexto;
}
