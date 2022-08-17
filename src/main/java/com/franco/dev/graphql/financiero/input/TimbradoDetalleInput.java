package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class TimbradoDetalleInput {
    private Long id;

    private Long timbradoId;

    private Long puntoDeVentaId;

    private String puntoExpedicion;

    private Long cantidad;

    private Long rangoDesde;

    private Long rangoHasta;

    private Long numeroActual;

    private Boolean activo;

    private Long usuarioId;
}
