package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import lombok.Data;

/**
 * Input para marcar como rechazados los items pendientes al finalizar recepción.
 * Un motivo general aplicado a todos los items pendientes.
 *
 * Usado en:
 * - Desktop: No
 * - Mobile: Sí (flujo de finalizar con pendientes)
 */
@Data
public class RechazoPendientesInput {
    private MotivoRechazoFisico motivoRechazo;
}
