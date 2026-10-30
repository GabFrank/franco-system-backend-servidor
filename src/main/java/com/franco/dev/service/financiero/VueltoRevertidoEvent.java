package com.franco.dev.service.financiero;

/**
 * Se anuló un vuelto: el egreso {@code egresoId} vuelve a tener pendiente. Se publica dentro de la
 * transacción de la anulación (listener sincrónico), así lo que dependa del vuelto se corrige junto.
 */
public class VueltoRevertidoEvent {

    private final Long egresoId;

    public VueltoRevertidoEvent(Long egresoId) {
        this.egresoId = egresoId;
    }

    public Long getEgresoId() {
        return egresoId;
    }
}
