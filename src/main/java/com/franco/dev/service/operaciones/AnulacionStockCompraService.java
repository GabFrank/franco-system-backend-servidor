package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.repository.operaciones.RecepcionMercaderiaItemRepository;
import com.franco.dev.service.configuraciones.ModificacionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Saca del stock lo que entró por la recepción de una compra que se cancela (#535).
 *
 * Los movimientos COMPRA no se borran: quedan con {@code estado = false}, igual que los de una
 * venta cancelada, así el stock (que suma solo los activos) baja y el movimiento queda a la vista
 * en el historial. El desglose por lote sigue al movimiento y cada anulación queda en el registro
 * de modificaciones con el usuario que canceló.
 */
@Service
@RequiredArgsConstructor
public class AnulacionStockCompraService {

    static final String TIPO_AUDITORIA = "ANULACION_STOCK_COMPRA";

    private static final Logger logger = LoggerFactory.getLogger(AnulacionStockCompraService.class);

    private final RecepcionMercaderiaItemRepository recepcionMercaderiaItemRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final MovimientoStockService movimientoStockService;
    private final MovimientoStockLoteService movimientoStockLoteService;
    private final ModificacionService modificacionService;

    /**
     * Anula los movimientos COMPRA activos de todos los ítems recibidos del pedido.
     *
     * @return cuántos movimientos se anularon (0 si la compra no tenía mercadería recibida)
     */
    @Transactional
    public int anularStockDePedido(Long pedidoId) {
        List<RecepcionMercaderiaItem> items = recepcionMercaderiaItemRepository.findByPedidoId(pedidoId);
        int anulados = 0;
        for (RecepcionMercaderiaItem item : items) {
            List<MovimientoStock> movimientos = movimientoStockRepository
                    .findByTipoMovimientoAndReferenciaAndEstadoTrue(TipoMovimiento.COMPRA, item.getId());
            for (MovimientoStock movimiento : movimientos) {
                MovimientoStock anterior = new MovimientoStock();
                BeanUtils.copyProperties(movimiento, anterior);

                movimiento.setEstado(false);
                MovimientoStock guardado = movimientoStockService.save(movimiento);
                // Las filas por lote tienen su propio estado: si no se apagan, el stock por lote
                // seguiría contando lo que se sacó del stock general.
                movimientoStockLoteService.sincronizarEstado(guardado);
                modificacionService.registrarActualizacion(anterior, guardado, TIPO_AUDITORIA,
                        "operaciones", "movimiento_stock");
                anulados++;
            }
        }
        logger.info("Cancelación del pedido {}: {} movimiento(s) de stock COMPRA anulado(s)", pedidoId, anulados);
        return anulados;
    }
}
