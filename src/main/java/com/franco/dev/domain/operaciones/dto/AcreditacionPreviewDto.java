package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.personas.Proveedor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Preview de consolidacion para acreditar un retiro: proveedor + lineas por
 * producto + monto total sugerido (a costo medio). El usuario ajusta antes de
 * confirmar la nota de credito.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AcreditacionPreviewDto {
    private Long retiroId;
    private Proveedor proveedor;
    private List<AcreditacionPreviewLineaDto> lineas;
    private Double montoTotal;
}
