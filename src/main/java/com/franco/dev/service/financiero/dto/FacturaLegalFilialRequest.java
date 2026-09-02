package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaLegalFilialRequest {
    private Long timbradoDetalleId;
    private Long cajaId;
    private Long clienteId;
    /**
     * Datos del cliente para que el filial pueda materializarlo si no lo tiene todavia.
     * Va lleno siempre que clienteId no sea null.
     */
    private ClienteFilialRequest cliente;
    /** Usuario que emite la factura. Sin esto la factura queda sin trazabilidad en el filial. */
    private Long usuarioId;
    private String nombre;
    private String ruc;
    private String direccion;
    private String email;
    private Boolean viaTributaria;
    private Boolean credito;
    private String fecha;
    private List<FacturaLegalItemFilialRequest> items;
    private Double ivaParcial0;
    private Double ivaParcial5;
    private Double ivaParcial10;
    private Double totalParcial0;
    private Double totalParcial5;
    private Double totalParcial10;
    private Double totalFinal;
    private Double descuento;
    private String monedaExtranjera;
    private Double tipoCambio;
}

