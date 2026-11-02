package com.franco.dev.graphql.operaciones.compras;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompraPedidoMobile {
    private Long pedidoId;
    private Long proveedorId;
    private String proveedorNombre;
    private String proveedorRuc;
    private String fecha;
    private String monedaDenominacion;
    private String monedaSimbolo;
    private Double cotizacion;
    private String estadoEtapa;
    /** RECEPCION_NOTA en PENDIENTE o EN_PROCESO: se pueden cargar facturas. */
    private Boolean puedeCargarFacturas;
    /** RECEPCION_NOTA en EN_PROCESO y al menos una nota. */
    private Boolean puedeFinalizar;
    private String motivoNoOpera;
    private Double totalPedido;
    private Double totalFacturado;
    private Integer itemsPendientes;
    private List<CompraPedidoItemMobile> items = new ArrayList<>();
    private List<CompraNotaMobile> notas = new ArrayList<>();
}
