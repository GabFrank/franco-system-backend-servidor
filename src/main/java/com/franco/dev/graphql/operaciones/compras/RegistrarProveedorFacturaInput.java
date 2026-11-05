package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.List;

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
    /**
     * Con persona existente: los campos (nombre, nombreFantasia, direccion, telefono, email) que se actualizan con
     * lo enviado aunque la persona ya los tenga. El resto solo se completa si está vacío.
     */
    private List<String> actualizarPersona;
}
