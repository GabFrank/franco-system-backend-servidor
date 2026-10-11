package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.util.List;

/**
 * Datos del viaje que la PWA pide antes de verificar una transferencia para transporte. El
 * usuario chofer queda como responsable de la etapa y su persona como chofer de la hoja de ruta.
 */
@Data
public class VerificarParaTransporteInput {
    private Long transferenciaId;
    private Long choferUsuarioId;
    private Long vehiculoId;
    private List<Long> acompanantesIds;
}
