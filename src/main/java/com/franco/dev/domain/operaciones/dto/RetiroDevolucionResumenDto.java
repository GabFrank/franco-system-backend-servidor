package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Una devolucion lista para retirar, con sus productos. Es la unidad que se
 * escanea en el retiro: su etiqueta lleva un solo QR con el identificador.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroDevolucionResumenDto {
    private Long devolucionId;
    /** DEV-{sucursalId}-{id}: lo que trae el QR de la etiqueta. */
    private String identificador;
    private String estado;
    /** Productos de la devolucion (una entrada por item). */
    private List<RetiroCajaDto> productos;
}
