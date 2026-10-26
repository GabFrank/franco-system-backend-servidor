package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** La cabecera de la factura en papel revisada por el comprador. */
@Data
public class CabeceraFacturaFotoInput {
    private Long documentoId;
    private Long proveedorId;
    private Integer timbrado;
    private String establecimiento;
    private String puntoExpedicion;
    private String numero;
    /** yyyy-MM-dd (también acepta yyyy-MM-dd HH:mm) */
    private String fechaEmision;
    /** CONTADO | CREDITO (null: contado) */
    private String condicion;
    private Double totalGeneral;
}
