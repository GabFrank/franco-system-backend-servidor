package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resultado agregado del retiro en bloque de varias devoluciones.
 * Cada devolucion se procesa de forma independiente; el fallo de una no aborta el resto.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RetiroBloqueResultadoDto {
    private List<RetiroDevolucionResultadoDto> resultados;
}
