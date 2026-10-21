package com.franco.dev.graphql.operaciones.compras;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Una opción de un select: forma de pago o sucursal. */
@Data
@AllArgsConstructor
public class OpcionMobile {
    private Long id;
    private String descripcion;
}
