package com.franco.dev.domain.financiero.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Totales de un RUC en el mes. Los montos gravados son la base SIN IVA (como los pide el
 * formulario 120); en factura_legal el total por tasa incluye el IVA.
 */
@Data
public class ResumenFiscalContribuyente {
    private String ruc;
    private String razonSocial;
    private Double gravada10;
    private Double iva10;
    private Double gravada5;
    private Double iva5;
    private Double exentas;
    /** gravada10 + gravada5 + exentas. */
    private Double totalBase;
    /** iva10 + iva5. */
    private Double totalIva;
    /** Lo facturado con IVA: totalBase + totalIva. */
    private Double totalFacturado;
    private Long emitidas;
    private Long anuladas;
    private List<ResumenFiscalTimbrado> detalle = new ArrayList<>();
}
