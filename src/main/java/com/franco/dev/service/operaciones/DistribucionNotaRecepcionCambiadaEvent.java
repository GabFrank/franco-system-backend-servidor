package com.franco.dev.service.operaciones;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * Cambiaron las distribuciones de ítems de estas notas de recepción, y el estado de cada nota
 * puede tener que pasar a CONCILIADA o volver a PENDIENTE_CONCILIACION.
 *
 * Lo publica {@link NotaRecepcionItemDistribucionService} al guardar o reemplazar distribuciones y
 * lo escucha {@link NotaRecepcionService#alCambiarDistribucion}, que recalcula el estado. El listener
 * es sincrónico: corre en el mismo hilo y en la misma transacción, en el punto donde se publica.
 *
 * Existe para que el service de distribuciones no dependa de {@link NotaRecepcionService}, que a su
 * vez depende de él (ciclo de beans, issue #651).
 */
@Getter
public class DistribucionNotaRecepcionCambiadaEvent {

    private final Set<Long> notaRecepcionIds;

    public DistribucionNotaRecepcionCambiadaEvent(Set<Long> notaRecepcionIds) {
        this.notaRecepcionIds = Collections.unmodifiableSet(notaRecepcionIds);
    }
}
