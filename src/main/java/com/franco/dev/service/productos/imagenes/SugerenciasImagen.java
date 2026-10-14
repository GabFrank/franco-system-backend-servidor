package com.franco.dev.service.productos.imagenes;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Resultado de buscar fotos para una presentación: por código de barras y por descripción. */
@Data
public class SugerenciasImagen {

    /** Código que se buscó. Null si la presentación no tiene uno comercial. */
    private String codigo;
    /** Código que se pidió o que tiene la presentación pero es interno (no existe fuera del local): no se buscó. */
    private String codigoDescartado;
    /** Texto que se buscó. */
    private String texto;
    private List<ImagenSugerida> porCodigo = new ArrayList<>();
    private List<ImagenSugerida> porDescripcion = new ArrayList<>();
    /** Fuentes que no respondieron o no están configuradas, para mostrar al usuario. */
    private List<String> avisos = new ArrayList<>();
}
