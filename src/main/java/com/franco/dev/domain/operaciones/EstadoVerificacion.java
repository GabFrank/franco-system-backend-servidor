package com.franco.dev.domain.operaciones;

/**
 * Enum que representa el estado de verificación de un ítem de recepción de mercadería.
 * Este enum permite rastrear el estado de cada ítem desde su creación hasta su verificación completa.
 */
public enum EstadoVerificacion {
    
    /**
     * El ítem está pendiente de verificación por parte del usuario.
     * Estado inicial cuando se crea el ítem en iniciarRecepcion.
     */
    PENDIENTE,
    
    /**
     * El ítem ha sido verificado completamente.
     * Se establece cuando cantidadRecibida == cantidadEsperada.
     */
    VERIFICADO,
    
    /**
     * El ítem ha sido verificado pero con diferencias en las cantidades.
     * Se establece cuando cantidadRecibida != cantidadEsperada.
     */
    VERIFICADO_CON_DIFERENCIA,
    
    /**
     * El ítem ha sido rechazado durante la verificación.
     * Se establece cuando hay cantidadRechazada > 0.
     */
    RECHAZADO
}
