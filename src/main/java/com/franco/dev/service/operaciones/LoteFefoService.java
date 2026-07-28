package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Asignación de lotes por FEFO (First Expired, First Out).
 *
 * Decide de qué lotes sale la mercadería cuando hay una salida de stock (transferencia hoy, venta
 * más adelante). Es la contraparte de la entrada por compra: ahí el lote lo informa el operador,
 * acá lo resuelve el sistema.
 */
@Service
@AllArgsConstructor
public class LoteFefoService {

    private final MovimientoStockLoteService movimientoStockLoteService;

    /**
     * Una porción de la cantidad total, asignada a un lote concreto.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AsignacionLote {
        private Long loteId;
        private String numeroLote;
        private Double cantidad;
    }

    /**
     * Reparte {@code cantidad} entre los lotes disponibles de un producto en una sucursal,
     * priorizando los que hay que sacar antes.
     *
     * Reglas:
     * - Ordena por fecha de retiro (o vencimiento si no hay retiro), los sin fecha al final.
     *   El orden viene resuelto por la consulta de saldo.
     * - Excluye los lotes que no están LIBERADO: así funciona el bloqueo por recall.
     * - Si el saldo por lote no alcanza para cubrir la cantidad, asigna lo que hay y devuelve
     *   menos. La operación NO se bloquea: el stock agregado sigue siendo la fuente de verdad del
     *   total, y el desglose por lote es un subconjunto trazable. Esto cubre el stock histórico
     *   cargado antes de que el producto tuviera control de lote.
     *
     * @return las asignaciones, en orden FEFO. Vacío si no hay nada disponible.
     */
    public List<AsignacionLote> asignar(Long productoId, Long sucursalId, Double cantidad) {
        List<AsignacionLote> asignaciones = new ArrayList<>();
        if (productoId == null || sucursalId == null || cantidad == null || cantidad <= 0) {
            return asignaciones;
        }

        List<StockLoteDto> disponibles = movimientoStockLoteService.stockPorLote(productoId, sucursalId);
        double pendiente = cantidad;

        for (StockLoteDto lote : disponibles) {
            if (pendiente <= 0.0001) {
                break;
            }
            if (lote.getLoteId() == null || lote.getEstado() != EstadoLote.LIBERADO) {
                continue;
            }
            Double saldo = lote.getCantidadDisponible();
            if (saldo == null || saldo <= 0) {
                continue;
            }
            double aTomar = Math.min(saldo, pendiente);
            asignaciones.add(new AsignacionLote(lote.getLoteId(), lote.getNumeroLote(), aTomar));
            pendiente -= aTomar;
        }

        return asignaciones;
    }
}
