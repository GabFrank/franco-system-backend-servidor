package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

/**
 * Alta del proveedor desde una factura recibida. El RUC sale siempre del documento guardado en el central; el
 * usuario solo puede corregir los textos que se precargaron.
 */
@Data
public class RegistrarProveedorFacturaInput {
    private Long documentoId;
    private String nombre;
    private String nombreFantasia;
    private String direccion;
    private String telefono;
    private String email;
    private Boolean credito;
    private Integer chequeDias;
}
