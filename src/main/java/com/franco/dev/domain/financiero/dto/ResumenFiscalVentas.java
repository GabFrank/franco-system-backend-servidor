package com.franco.dev.domain.financiero.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen fiscal de las ventas facturadas de un mes, para pasarle al contador: base e IVA por
 * tasa, la vista por rubros del formulario 120 y el detalle por timbrado. Una seccion por RUC,
 * porque el contador declara por contribuyente.
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
