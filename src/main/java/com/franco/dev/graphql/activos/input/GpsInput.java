package com.franco.dev.graphql.activos.input;

import lombok.Data;

@Data
public class GpsInput {
    private Long id;
    private String imei;
    private Long vehiculoId;
    private String modeloTracker;
    private String simNumero;
    private Boolean activo;
}
