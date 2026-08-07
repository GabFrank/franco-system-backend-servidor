package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

/**
 * Una porcion de la cantidad de un item de transferencia, asignada a un lote concreto por el
 * operador. Llega como lista dentro de {@link TransferenciaItemInput}.
 */
@Data
public class TransferenciaItemLoteInput {
    private Long loteId;
    /**
     * EN PRESENTACIONES, que es la unidad con la que carga el operador. El backend la convierte a
     * unidades antes de persistir, con la misma regla que uso para mostrarle el saldo disponible.
     */
    private Double cantidad;
}
