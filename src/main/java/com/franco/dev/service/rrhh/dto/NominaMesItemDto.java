package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Fila del reporte de nómina del mes. Campos = <field> de nomina-mes.jrxml. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NominaMesItemDto {
    private String funcionario;
    private String haberes;
    private String descuentos;
    private String neto;
    /** "BANCO" o "EFECTIVO": grupo del reporte, sale del flag cobraBanco del funcionario. */
    private String formaCobro;
    /** Neto sin formatear: lo suma Jasper para los subtotales por forma de cobro. */
    private BigDecimal netoNum;
}
