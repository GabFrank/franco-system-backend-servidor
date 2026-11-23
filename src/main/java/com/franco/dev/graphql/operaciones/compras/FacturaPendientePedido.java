package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Una factura del pedido que espera la planificación y tiene renglones que no se cargaron (#124). Los montos van en
 * la moneda de la factura.
 */
@Data
public class FacturaPendientePedido {
    private Long documentoId;
    private String numeroCompleto;
    private String monedaSimbolo;
    private Double montoPendiente;
    private Double montoDescartado;
    private List<RenglonPendienteFactura> renglones = new ArrayList<>();
}
