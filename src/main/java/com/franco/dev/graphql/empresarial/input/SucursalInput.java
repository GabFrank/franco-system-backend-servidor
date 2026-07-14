package com.franco.dev.graphql.empresarial.input;

import lombok.Data;

@Data
public class SucursalInput {
    private Long id;
    private String nombre;
    private String localizacion;
    private Long ciudadId;
    private Boolean deposito;
    private Boolean depositoPredeterminado;
    private Long usuarioId;
    private String direccion;
    private String nroDelivery;
    private Boolean isConfigured;
    private String codigoEstablecimientoFactura;
    private String creadoEn;
    private String ip;
    private Integer puerto;
    private Integer puertoServidor;
    private Boolean activo;

}
