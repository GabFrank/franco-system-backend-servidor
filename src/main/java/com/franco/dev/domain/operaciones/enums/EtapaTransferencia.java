package com.franco.dev.domain.operaciones.enums;

/**
 * Etapas por las que pasa una transferencia, en orden de avance.
 *
 * El flujo es de una sola direccion: una transferencia solo avanza. El {@code orden} es explicito
 * y no derivado de {@link #ordinal()} para que reordenar las constantes no cambie la regla sin
 * que nadie se de cuenta.
 */
public enum EtapaTransferencia {
    PRE_TRANSFERENCIA_CREACION(1),
    PRE_TRANSFERENCIA_ORIGEN(2),
    PREPARACION_MERCADERIA(3),
    PREPARACION_MERCADERIA_CONCLUIDA(4),
    TRANSPORTE_VERIFICACION(5),
    TRANSPORTE_EN_CAMINO(6),
    TRANSPORTE_EN_DESTINO(7),
    RECEPCION_EN_VERIFICACION(8),
    RECEPCION_CONCLUIDA(9);

    private final int orden;

    EtapaTransferencia(int orden) {
        this.orden = orden;
    }

    public int getOrden() {
        return orden;
    }

    /**
     * True si desde esta etapa se puede pasar a {@code destino}.
     *
     * Se permite avanzar y se permite reenviar la etapa actual (no mueve nada). Se permite saltear
     * hacia adelante porque el flujo real lo hace: de TRANSPORTE_EN_CAMINO se va derecho a
     * RECEPCION_EN_VERIFICACION y TRANSPORTE_EN_DESTINO no se usa. Retroceder nunca se permite.
     */
    public boolean puedeAvanzarA(EtapaTransferencia destino) {
        if (destino == null) return false;
        return destino.orden >= this.orden;
    }
}
