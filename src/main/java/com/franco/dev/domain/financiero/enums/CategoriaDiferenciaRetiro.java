package com.franco.dev.domain.financiero.enums;

/**
 * Por qué no coincidió lo contado con lo declarado, en una moneda.
 *
 * Tipificada y no texto libre: es lo que después permite preguntar "cuántos faltantes en
 * dólares tuvo la sucursal 3 este mes". Un campo de texto no responde eso.
 *
 * Vive en el detalle y no en la cabecera porque un mismo retiro puede ser FALTANTE en una
 * moneda y SOBRANTE en otra a la vez.
 */
public enum CategoriaDiferenciaRetiro {
    DIFERENCIA_CONTEO,
    FALTANTE,
    SOBRANTE,
    /** El monto está pero la pieza no sirve: billete falso o roto. No se trata igual. */
    BILLETE_NO_RECIBIBLE,
    /** El retiro existe en el sistema y físicamente nunca llegó. */
    NO_RECIBIDO,
    OTRO
}
