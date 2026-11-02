package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un producto dentro de la etiqueta de una devolucion. Es una linea de la
 * lista, no una etiqueta aparte: la etiqueta es una sola por devolucion.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EtiquetaDevolucionFilaDto {
    private String codigo;
    /** Id + descripcion del producto. */
    private String producto;
    /** Ej. "3 UNIDAD x1 = 3 UN". */
    private String cantidad;
    private String motivo;
}
