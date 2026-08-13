package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Devolucion/producto que lleva mucho tiempo en PENDIENTE o SEPARADO sin
 * avanzar. Para el panel de "estancadas" del dashboard.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DevolucionEstancadaDto {
    private Long devolucionId;
    private String identificador;
    private String producto;
    private String sucursal;
    private String estado;
    /** Fecha de creacion (YYYY-MM-DD). */
    private String fecha;
    /** Dias transcurridos desde la fecha. */
    private Long dias;
}
