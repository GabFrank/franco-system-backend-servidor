package com.franco.dev.graphql.operaciones.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CobroInput {
    private Long id;
    private LocalDateTime creadoEn;
    private Long usuarioId;
    private Double totalGs;
    private Long sucursalId;
}
