package com.franco.dev.graphql.empresarial.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ZonaInput {
    private Long id;
    private String descripcion;
    private Long sectorId;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
