package com.franco.dev.graphql.activos.input;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VehiculoInput {
    private Long id;
    private Long modeloId;
    private Long tipoVehiculoId;
    private String chapa;
    private String color;
    private Integer anho;
    private Boolean documentacion;
    private Boolean refrigerado;
    private Boolean nuevo;
    private String fechaAdquisicion;
    private BigDecimal primerKilometraje;
    private BigDecimal capacidadKg;
    private Integer capacidadPasajeros;
    private String imagenesVehiculo;
    private String imagenesDocumentos;
    private Long usuarioId;
}

