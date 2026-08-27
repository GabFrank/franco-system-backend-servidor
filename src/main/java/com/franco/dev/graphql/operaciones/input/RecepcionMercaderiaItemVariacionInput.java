package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import lombok.Data;

@Data
public class RecepcionMercaderiaItemVariacionInput {
    private Long presentacionId;
    private Double cantidad;
    private String vencimiento;
    /** Opcional. Si viene, pisa el calculo automatico de la fecha de retiro del lote. */
    private String fechaRetiro;
    private String lote;
    private Boolean rechazado;
    private MotivoRechazoFisico motivoRechazo;
}
