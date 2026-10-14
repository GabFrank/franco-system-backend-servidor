package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Asistencia de un funcionario en un mes: totales, puntaje y detalle dia por dia. */
@Data
@AllArgsConstructor
public class AsistenciaResumenDto {
    private Integer anho;
    private Integer mes;
    private Boolean tieneHorario;
    private Integer diasEsperados;
    private Integer diasTrabajados;
    private Integer llegadasTardias;
    private Integer ausenciasJustificadas;
    private Integer ausenciasInjustificadas;
    /** Porcentaje de dias trabajados sin llegada tardia. Null si no hubo dias trabajados. */
    private Integer puntualidad;
    /** 0..10 con un decimal. Null si no hubo dias esperados. */
    private Double puntaje;
    private List<AsistenciaDiaDto> dias;
}
