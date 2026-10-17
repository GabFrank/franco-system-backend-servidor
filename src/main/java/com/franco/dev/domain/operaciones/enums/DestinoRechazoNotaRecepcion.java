package com.franco.dev.domain.operaciones.enums;

/** A qué nota va el ítem rechazado desde la recepción documental. */
public enum DestinoRechazoNotaRecepcion {
    /** La nota elegida (o la del ítem, si se rechaza un ítem que ya está en una nota). */
    NOTA_EXISTENTE,
    /** Una nota nueva del pedido. */
    NOTA_NUEVA,
    /** Una nota nueva «de rechazo» (producto no entregado). */
    NOTA_RECHAZO
}
