package com.franco.dev.domain.financiero.dto;

import lombok.Data;

/** Facturas del mes de un punto de expedicion de un timbrado (una sucursal). */
@Data
public class ResumenFiscalTimbrado {
    private Long sucursalId;
    private String sucursal;
    private String timbrado;
    private Boolean electronico;
    /** "Electrónica" o "Papel". */
    private String tipo;
    private String establecimiento;
    private String puntoExpedicion;
    /** Primer y ultimo numero usados en el mes, anuladas incluidas: "001-001-0156442". */
    private String numeroDesde;
    private String numeroHasta;
    private Long emitidas;
    private Long anuladas;
    private Long fueraDeVigencia;
    private Double gravada10;
    private Double iva10;
    private Double gravada5;
    private Double iva5;
    private Double exentas;
    private Double totalFacturado;
}
