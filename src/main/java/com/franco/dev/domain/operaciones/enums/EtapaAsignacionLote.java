package com.franco.dev.domain.operaciones.enums;

/**
 * Etapa en la que el operador eligio los lotes de un item de transferencia.
 *
 * No es lo mismo que {@link EtapaTransferencia}: aca solo interesan los dos momentos en los que
 * tiene sentido elegir un lote. En creacion es una intencion (todavia no hay movimiento de stock);
 * en preparacion es lo que realmente se saco del deposito.
 */
public enum EtapaAsignacionLote {
    /** Elegido al cargar el item, antes de que exista movimiento de stock. */
    PRE_TRANSFERENCIA,
    /** Elegido al preparar la mercaderia. Tiene prioridad sobre PRE_TRANSFERENCIA. */
    PREPARACION
}
