package com.franco.dev.service.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del reporte de resumen IPS. Campos = <field> de resumen-ips.jrxml. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenIpsItemDto {
    private String funcionario;
    private String salarioBase;
    private String ipsFuncionario;
    private String ipsPatronal;
}
