package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.PagoEstado;
import lombok.Data;

@Data
public class PagoInput {
    private Long id;
    private String creadoEn;
    private Long usuarioId;
    private Long autorizadoPorId;
    private PagoEstado estado;
    private Boolean programado;
}
