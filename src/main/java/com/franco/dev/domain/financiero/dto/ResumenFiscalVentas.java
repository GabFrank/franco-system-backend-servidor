package com.franco.dev.domain.financiero.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen de las ventas facturadas de un mes, para pasarle al contador: exentas, gravadas 5 % y
 * gravadas 10 % (base e IVA) y el detalle por timbrado. Solo datos: armar la declaracion es
 * trabajo del contador. Una seccion por RUC, porque el contador declara por contribuyente.
 */
@Data
public class ResumenFiscalVentas {
    private Integer anio;
    private Integer mes;
    /** "Julio 2026". */
    private String periodo;
    /** "Todas" o los nombres de las sucursales filtradas. */
    private String sucursalesFiltro;
    private List<ResumenFiscalContribuyente> contribuyentes = new ArrayList<>();
}
