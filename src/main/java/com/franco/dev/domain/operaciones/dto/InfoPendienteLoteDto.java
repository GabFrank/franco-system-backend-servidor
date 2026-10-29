package com.franco.dev.domain.operaciones.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Lo que el dialogo de completar necesita mostrar antes de confirmar. */
@Data
public class InfoPendienteLoteDto {
    private PendienteLoteDto pendiente;
    /** SIN LOTE disponible del producto en la sucursal (existencia - lotes reales), segun el central. */
    private Double saldoSinLote;
    /** Unidades atribuidas a mano (ajustes ATRIBUIR fuera de esta pantalla) despues de la recepcion. */
    private Double atribuidoManualDespues;
    /** min(remanente, saldoSinLote): lo que se propone atribuir. */
    private Double cantidadSugerida;
    /** Ultimo mensaje recibido de la replicacion de esa sucursal (null = no se sabe). */
    private LocalDateTime ultimaSincronizacion;
    /** true = no se puede completar: la sucursal no sincronizo en los ultimos minutos (o no se sabe). */
    private Boolean sincronizacionVencida;
    private Integer maxDesfaseMinutos;
    /** Lotes del producto con un numero igual o parecido al buscado (para reutilizar en vez de crear). */
    private List<LoteEncontradoDto> lotesParecidos = new ArrayList<>();
}
