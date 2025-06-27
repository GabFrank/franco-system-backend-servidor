package com.franco.dev.domain.operaciones.enums;

public enum PedidoEstado {

    // Creation phase - pedido is being created and items are being added
    ABIERTO, // Initial state when pedido is first created (no items yet)
    ACTIVO, // Creation phase with items added (ready to proceed to nota reception)

    // Reception phases - sequential workflow steps
    EN_RECEPCION_NOTA, // Step 2: Assigning items to nota recepcion entities
    EN_RECEPCION_MERCADERIA, // Step 3: Verifying received merchandise
    EN_SOLICITUD_PAGO, // Step 4: Creating payment request groups
    
    // Final states
    CONCLUIDO, // All steps completed successfully
    CANCELADO // Pedido was cancelled at any stage

}
