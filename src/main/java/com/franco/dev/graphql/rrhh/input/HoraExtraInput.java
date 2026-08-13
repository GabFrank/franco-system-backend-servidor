package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.HoraExtraOrigen;
import com.franco.dev.domain.rrhh.enums.HoraExtraTipo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class HoraExtraInput {
    private Long id;
    private Long funcionarioId;
    private String fecha;
    private Long jornadaId;
    private Long sucursalId;
    private BigDecimal minutos;
    private HoraExtraTipo tipo;
    private BigDecimal recargoPorcentaje;
    private BigDecimal montoCalculado;
    private HoraExtraOrigen origen;
    private Boolean anulada;
    private Long autorizadoPorId;
    private String observacion;
}
