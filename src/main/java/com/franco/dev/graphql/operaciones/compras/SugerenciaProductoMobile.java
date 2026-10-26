package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** Un producto nuestro que puede ser el del renglón, y por qué se sugiere. */
@Data
public class SugerenciaProductoMobile {
    private Long productoId;
    private Long presentacionId;
    private String descripcion;
    private String presentacionDescripcion;
    private Double factor;
    private String codigo;
    /** APRENDIDO_DESCRIPCION, CODIGO_PARECIDO, DESCRIPCION, PROVEEDOR */
    private String tipo;
    private String motivo;
    /** Lo aprendido por la descripción, sin ambigüedad: la PWA la ofrece para confirmar con un toque. */
    private Boolean preseleccionada;
}
