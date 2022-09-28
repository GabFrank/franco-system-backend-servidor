package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import lombok.Data;

@Data
public class MovimientoStockInput {
    private Long id;
    private Long sucursalId;
    private Long productoId;
    private Long codigoId;
    private TipoMovimiento tipoMovimiento;
    private Integer referencia;
    private Float cantidad;
    private Boolean estado;
    private Long usuarioId;
}
