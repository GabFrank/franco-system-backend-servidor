package com.franco.dev.domain.operaciones.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuanto se puede confiar en un "no hay registro de este lote" para un producto.
 *
 * El sistema solo puede afirmar algo desde que el producto se controla por lote (trazadoDesde) y
 * solo si todo lo que entro desde entonces quedo con lote. Por eso la respuesta lleva la fecha y
 * lo que entro sin lote, y la afirmacion fuerte (todasLasEntradasConLote) es conservadora.
 */
@Data
@NoArgsConstructor
public class TrazabilidadProductoDto {
    private Long productoId;
    private String productoDescripcion;
    /** Primer movimiento con lote REAL del producto. Nulo: el producto nunca se controlo por lote. */
    private LocalDateTime trazadoDesde;
    /** Entradas (cantidad positiva) desde trazadoDesde que no quedaron enteras en un lote. */
    private List<EntradaSinLoteDto> entradasSinLote = new ArrayList<>();
    /** Compras del producto anteriores a trazadoDesde: su lote el sistema no lo conoce. */
    private Long comprasAnteriores;
    /**
     * true solo si hay trazado, ninguna entrada sin lote desde entonces y ninguna compra anterior.
     * Es la unica condicion en la que la pantalla puede decir "todas las entradas tienen lote".
     */
    private Boolean todasLasEntradasConLote;
}
