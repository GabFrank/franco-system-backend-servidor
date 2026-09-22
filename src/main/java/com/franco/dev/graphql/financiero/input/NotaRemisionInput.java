package com.franco.dev.graphql.financiero.input;

import lombok.Data;

/**
 * Cabecera de la nota de remisión tal como la manda el desktop. Las fechas viajan como String
 * (convención del repo) y los enums como nombre.
 */
@Data
public class NotaRemisionInput {

    private Long id;
    private Long sucursalId;
    private Long timbradoDetalleId;
    private String fecha;

    private String origen;
    private Long transferenciaId;
    private Long facturaLegalId;

    private String motivoEmision;
    private String responsableEmision;
    private Integer kmEstimado;
    private String fechaInicioTraslado;
    private String fechaFinTraslado;
    private String fechaEstimadaFactura;

    private Long clienteId;
    private String receptorNombre;
    private String receptorRuc;
    private String receptorDireccion;
    private String receptorDepartamento;
    private Integer receptorCodigoCiudad;
    private String receptorCiudad;

    private String salidaDireccion;
    private String salidaDepartamento;
    private Integer salidaCodigoCiudad;
    private String salidaCiudad;
    private String entregaDireccion;
    private String entregaDepartamento;
    private Integer entregaCodigoCiudad;
    private String entregaCiudad;

    private String tipoTransporte;
    private String modalidadTransporte;
    private String transportistaNombre;
    private String transportistaRuc;
    private String transportistaDireccion;

    private Long vehiculoId;
    private String vehiculoMarca;
    private String vehiculoMatricula;
    private Long choferPersonaId;
    private String choferNombre;
    private String choferDocumento;
    private String choferDireccion;

    private Long usuarioId;
}
