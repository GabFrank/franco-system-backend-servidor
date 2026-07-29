package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Una porcion de la cantidad de un item de transferencia, asignada a un lote concreto por el
 * operador. Llega como lista dentro de {@link TransferenciaItemInput}.
 */
@Data
public class TransferenciaItemLoteInput {
    private Long loteId;
    private Double cantidad;
}
