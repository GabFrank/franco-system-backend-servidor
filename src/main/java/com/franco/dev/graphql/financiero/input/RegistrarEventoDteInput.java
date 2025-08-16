package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class RegistrarEventoDteInput {
    private Long documentoElectronicoId;
    private Integer tipoEvento;
}


