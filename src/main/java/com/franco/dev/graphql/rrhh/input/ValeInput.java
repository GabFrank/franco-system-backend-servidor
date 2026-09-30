package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.ValeEstado;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ValeInput {
    private Long id;
    private Long funcionarioId;
    private Long motivoId;
    private BigDecimal monto;
    private Long monedaId;
    private String fecha;
    private ValeEstado estado;
    private Boolean esAdelanto;
    /** Null = no cambia (un desktop viejo no lo manda). */
    private Integer cantidadCuotas;
    private String observacion;
    private String comprobanteUrl;
    private Long autorizadoPorId;
    private Long usuarioId;
}
