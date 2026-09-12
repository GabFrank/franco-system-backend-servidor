package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Una region propuesta o editada.
 *
 * <p>No lleva {@code origen}: lo decide el camino por el que entra. Lo que se guarda desde el ABM
 * es MANUAL por definicion --una persona lo escribio-- y lo que llega por
 * {@code guardarRegionesDerivadas} es DERIVADA. Dejarlo en el input permitiria que un cliente
 * marcara como MANUAL una region derivada y la volviera intocable por error.
 */
@Data
public class FormatoTerminalPosRegionInput {

    private Long id;

    private Long formatoTerminalPosId;

    /** Clave del mapeo del formato, en camelCase: codigoAutorizacion, monto, terminal... */
    private String campo;

    /** La etiqueta impresa que ancla la region. Es lo que sobrevive a un cambio del ticket. */
    private String etiqueta;

    /** DERECHA | ABAJO | DENTRO. */
    private String posicion;

    /** TEXTO | NUMERO | FECHA. */
    private String tipo;

    private Boolean obligatorio;

    /** Pista geometrica normalizada 0..1. Las cuatro o ninguna. */
    private BigDecimal x1;
    private BigDecimal y1;
    private BigDecimal x2;
    private BigDecimal y2;

    private Integer orden;
}
