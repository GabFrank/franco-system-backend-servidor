package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.MotivoRechazoFisico;
import lombok.Data;

@Data
public class RecepcionMercaderiaItemVariacionInput {
    private Long presentacionId;
    private Double cantidad;
    private String vencimiento;
    private String lote;
    private Boolean rechazado;
    private MotivoRechazoFisico motivoRechazo;
}
