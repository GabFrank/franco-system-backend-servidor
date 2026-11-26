package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.MotivoAnulacionCheque;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Motivo y observación de la anulación de un cheque; uno solo vale para todos los cheques de un pago (#807). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnulacionChequeInput {
    private MotivoAnulacionCheque motivo;
    private String observacion;
}
