package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del reporte de nómina del mes. Campos = <field> de nomina-mes.jrxml. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NominaMesItemDto {
    private String funcionario;
    private String haberes;
    private String descuentos;
    private String neto;
}
