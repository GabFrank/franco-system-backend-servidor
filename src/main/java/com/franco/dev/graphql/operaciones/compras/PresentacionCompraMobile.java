package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Una presentación del producto para elegir al vincular un renglón de la factura. */
@Data
public class PresentacionCompraMobile {
    private Long id;
    private String descripcion;
    /** Unidades base de la presentación: es el factor por defecto. */
    private Double cantidad;
    private String codigo;
    /** La que el central sugiere para el renglón (bulto de la descripción, código de barras o unidad). */
    private Boolean sugerida;
    private String motivo;
}
