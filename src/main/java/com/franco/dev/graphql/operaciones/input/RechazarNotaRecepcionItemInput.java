package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.DestinoRechazoNotaRecepcion;
import lombok.Data;

/**
 * Rechazo de un ítem desde la recepción documental. Con `notaRecepcionItemId` se rechaza (total o parcialmente) un
 * ítem que ya está en una nota; sin él se crea un ítem rechazado para el `pedidoItemId`.
 */
@Data
public class RechazarNotaRecepcionItemInput {
    private Long pedidoItemId;
    private Long notaRecepcionItemId;
    private DestinoRechazoNotaRecepcion destino;
    /** Solo con NOTA_EXISTENTE y sin ítem existente. */
    private Long notaRecepcionId;
    private Long presentacionId;
    /** En unidades de la presentación elegida. */
    private Double cantidad;
    private String motivoRechazo;
    private String observacion;
}
