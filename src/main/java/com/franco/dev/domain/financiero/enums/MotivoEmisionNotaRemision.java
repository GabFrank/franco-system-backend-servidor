package com.franco.dev.domain.financiero.enums;

/**
 * Motivo del traslado (gCamNRE / iMotEmiNR). Espejo 1:1 por nombre de
 * {@code com.roshka.sifen.core.types.TiMotivTras}: se mapea con {@code valueOf(name())}.
 *
 * ⚠️ En frc-efact este mapeo esta roto: el front persiste "1".."14" y el backend hace
 * {@code TiMotivTras.valueOf("1")}, que siempre cae al default TRASLADO_POR_VENTAS. Por eso aca se
 * persiste el NOMBRE del enum, nunca el codigo.
 */
public enum MotivoEmisionNotaRemision {
    TRASLADO_POR_VENTAS,
    TRASLADO_POR_CONSIGNACION,
    EXPORTACION,
    TRASLADO_POR_COMPRA,
    IMPORTACION,
    TRASLADO_POR_DEVOLUCION,
    TRASLADO_ENTRE_LOCALES,
    TRASLADO_BIENES_TRANSFORMACION,
    TRASLADO_BIENES_REPARACION,
    TRASLADO_POR_EMISOR_MOVIL,
    EXHIBICION_O_DEMOSTRACION,
    PARTICIPACION_EN_FERIAS,
    TRASLADO_DE_ENCOMIENDAS,
    DECOMISO,
    OTRO
}
