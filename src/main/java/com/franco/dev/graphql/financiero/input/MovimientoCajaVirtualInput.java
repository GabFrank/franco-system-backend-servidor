package com.franco.dev.graphql.financiero.input;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import lombok.Data;

@Data
public class MovimientoCajaVirtualInput {
    private Long id;
    private Long cajaVirtualId;
    private CajaVirtualTipoMovimiento tipoMovimiento;
    private Double cantidad;
    private Long monedaId;
    private Long referenciaId;
    private String descripcion;
    private Long usuarioId;
    private Long cajaOrigenId;
    private Long cajaDestinoId;
    private Boolean activo;
}
