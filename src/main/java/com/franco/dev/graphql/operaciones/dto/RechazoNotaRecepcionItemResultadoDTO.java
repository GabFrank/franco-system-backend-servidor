package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechazoNotaRecepcionItemResultadoDTO {
    /** El ítem rechazado que quedó guardado. */
    private NotaRecepcionItem notaRecepcionItem;
    /** Cantidad rechazada en unidades base. */
    private Double cantidadEnUnidadesBase;
}
