package com.franco.dev.service.activos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetriaDTO {

    private Long gpsId;
    private String imei;
    private Long vehiculoId;
    private String vehiculoChapa;
    private String vehiculoModelo;
    private String vehiculoMarca;

    private Double latitud;
    private Double longitud;
    private Integer velocidad;
    private Integer direccion;
    private Boolean ignicion;
    private String alarma;

    private LocalDateTime fechaGps;
    private LocalDateTime fechaServidor;

    private Boolean activo;
    private String modeloTracker;
}
