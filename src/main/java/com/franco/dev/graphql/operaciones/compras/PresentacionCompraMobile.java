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
    /** Precio de la factura dividido por las unidades de esta presentación (con IVA). */
    private Double precioPorUnidad;
    /** «como la última compra (4.729)» o «+400 % sobre la última compra (4.729)». */
    private String comparacion;
    private Boolean cierraConUltimaCompra;
    /** En la sugerida, las unidades del bulto cuando no son las de la presentación (804 = 12 × 67). */
    private Double factorSugerido;
    private String motivo;
}
