package com.franco.dev.graphql.impresion;

/** Que comprobante genera {@link TicketEscposGraphQL#ticketEscpos} en el central. */
public enum TicketEscposTipo {
    /** Una factura legal (id + sucursal). Igual que "Reimprimir" de la lista de facturas. */
    FACTURA,
    /** El balance de cierre de una caja (id + sucursal). Igual que "Imprimir Cierre". */
    BALANCE
}
