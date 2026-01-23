package com.franco.dev.graphql.vehiculos.input;

import lombok.Data;

@Data
public class TelemetriaInput {
    private Long id;
    private Long dispositivoId;
    private String fechaGps; // Received as String from GraphQL
    private Double latitud;
    private Double longitud;
    private Integer velocidad;
    private Integer direccion;
    private Boolean ignicion;
    private String alarma;
    private String jsonData;
}
