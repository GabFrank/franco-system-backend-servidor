package com.franco.dev.graphql.operaciones.compras;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un dato que la persona existente ya tiene cargado. Si la factura trae otro distinto, viene en {@code factura}
 * para ofrecer actualizarlo; si no, {@code factura} es null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatoDePersonaMobile {
    /** nombre, nombreFantasia, direccion, telefono o email. */
    private String campo;
    private String actual;
    private String factura;
}
