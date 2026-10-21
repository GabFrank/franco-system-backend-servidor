package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Lo que el comprador elige al crear la compra desde la factura, cuando la factura no lo trae. */
@Data
public class OpcionesNuevaCompraMobile {
    private List<OpcionMobile> formasPago = new ArrayList<>();
    /** Sucursales con depósito y activas: las mismas que ofrece el desktop como sucursal de entrega. */
    private List<OpcionMobile> sucursales = new ArrayList<>();
}
