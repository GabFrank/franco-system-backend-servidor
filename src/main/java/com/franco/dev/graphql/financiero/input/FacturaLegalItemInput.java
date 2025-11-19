package com.franco.dev.graphql.financiero.input;

import lombok.Data;

import java.io.Serializable;

@Data
public class FacturaLegalItemInput implements Serializable {
    private Long id;
    private Long clienteId;
    private Long facturaLegalId;
    private Long ventaItemId;
    private Long productoId;
    private Double cantidad;
    private String descripcion;
    private String unidadMedida;
    private Double precioUnitario;
    private Integer iva;
    private Double total;
    private Long usuarioId;
    private Long sucursalId;
}
