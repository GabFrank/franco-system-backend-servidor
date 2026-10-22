package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/** La factura de un proveedor leída de SIFEN, con su KuDE en PDF (base64). */
@Data
public class KudeProveedorMobile {
    private Long documentoId;
    private String cdc;
    private String tipoDocumentoDescripcion;
    private String numeroCompleto;
    private String emisorNombre;
    private String emisorRuc;
    private String fechaEmision;
    private String condicion;
    private String monedaSimbolo;
    private Double totalGeneral;
    private Integer cantidadRenglones;
    private Boolean cancelada;
    private String nombreArchivo;
    private String pdf;
}
