package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Un dia laborable del mes evaluado. */
@Data
@AllArgsConstructor
public class AsistenciaDiaDto {
    private String fecha;
    private EstadoAsistenciaDia estado;
    private Integer minutosLlegadaTardia;
    private Integer minutosLlegadaTardiaAlmuerzo;
    /** Nombre del tipo de justificativo del dia, si tiene. */
    private String justificativo;
}
