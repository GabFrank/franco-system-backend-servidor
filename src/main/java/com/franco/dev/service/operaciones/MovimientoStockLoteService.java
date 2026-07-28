package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ledger de stock por lote. Ver {@link MovimientoStockLote} para el racional del diseño.
 */
@Service
@AllArgsConstructor
public class MovimientoStockLoteService
        extends CrudService<MovimientoStockLote, MovimientoStockLoteRepository, EmbebedPrimaryKey> {

    private final MovimientoStockLoteRepository repository;

    private final LoteService loteService;

    @Override
    public MovimientoStockLoteRepository getRepository() {
        return repository;
    }

    /**
     * Asigna el id antes de guardar siguiendo el esquema par/impar de operaciones.movimiento_stock:
     * el central genera SIEMPRE ids IMPARES y la filial PARES. Sin esto, ambos servidores
     * colisionarían en la PK (id, sucursal_id) apenas la filial empiece a descontar por venta.
     *
     * Espejo exacto de {@link MovimientoStockService#save(MovimientoStock)}.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MovimientoStockLote save(MovimientoStockLote entity) {
        if (entity.getId() == null) {
            if (entity.getCreadoEn() == null) {
                entity.setCreadoEn(LocalDateTime.now());
            }
            Long lastId = repository.findMaxId(entity.getSucursalId());
            if (lastId == null) {
                lastId = 0L;
            }
            // lastId par -> +1 (impar); lastId impar -> +2 (sigue impar).
            entity.setId(lastId % 2 != 0 ? lastId + 2 : lastId + 1);
        }
        return super.save(entity);
    }

    /**
     * Registra el desglose por lote de una entrada por compra.
     *
     * Se llama justo después de crear el {@link MovimientoStock} agregado y dentro de la misma
     * transacción. El movimiento agregado sigue siendo la fuente de verdad del stock total; esto
     * solo lo desglosa.
     *
     * Dos escenarios:
     *  - Con variaciones: una fila por variación no rechazada (múltiples lotes en la misma sucursal).
     *  - Sin variaciones: una sola fila con el lote y vencimiento del ítem.
     *
     * @return las filas creadas (vacío si el ítem no aporta información de lote).
     */
    @Transactional
    public List<MovimientoStockLote> registrarEntradaCompra(RecepcionMercaderiaItem item,
                                                            MovimientoStock movimiento) {
        List<MovimientoStockLote> creados = new ArrayList<>();
        if (item == null || movimiento == null) {
            return creados;
        }

        List<RecepcionMercaderiaItemVariacion> variaciones = item.getVariaciones();

        if (variaciones != null && !variaciones.isEmpty()) {
            creados.addAll(desglosarVariaciones(item, movimiento, variaciones));
            verificarInvariante(creados, movimiento, item);
            return creados;
        }

        String numeroLote = LoteService.normalizarNumeroLote(item.getLote());
        if (numeroLote == null) {
            log.warning("Producto con control de lote sin numero de lote en recepcion item "
                    + item.getId() + ": no se genera desglose por lote.");
            return creados;
        }
        Double cantidad = item.getCantidadRecibida();
        if (cantidad == null || cantidad <= 0) {
            return creados;
        }
        creados.add(crearMovimiento(item, movimiento, numeroLote, item.getVencimientoRecibido(), cantidad,
                item.getPresentacionRecibida()));
        verificarInvariante(creados, movimiento, item);
        return creados;
    }

    private List<MovimientoStockLote> desglosarVariaciones(RecepcionMercaderiaItem item,
                                                           MovimientoStock movimiento,
                                                           List<RecepcionMercaderiaItemVariacion> variaciones) {
        List<MovimientoStockLote> creados = new ArrayList<>();
        for (RecepcionMercaderiaItemVariacion variacion : variaciones) {
            boolean rechazada = variacion.getRechazado() != null && variacion.getRechazado();
            if (rechazada) {
                continue;
            }
            Double cantidad = variacion.getCantidad();
            if (cantidad == null || cantidad <= 0) {
                continue;
            }
            String numeroLote = LoteService.normalizarNumeroLote(variacion.getLote());
            if (numeroLote == null) {
                log.warning("Variacion sin numero de lote en recepcion item " + item.getId()
                        + ": no se desglosa esa cantidad.");
                continue;
            }
            LocalDate vencimiento = variacion.getVencimiento() != null
                    ? variacion.getVencimiento().toLocalDate()
                    : null;
            creados.add(crearMovimiento(item, movimiento, numeroLote, vencimiento, cantidad,
                    variacion.getPresentacion()));
        }
        return creados;
    }

    /**
     * Chequeo de integridad del diseño: la suma del desglose por lote tiene que dar exactamente
     * la cantidad del movimiento agregado. Si no da, el stock por lote y el stock total quedan
     * contando cosas distintas.
     *
     * Se registra como warning y no como excepción para no bloquear la finalización de una
     * recepción por datos historicos incompletos (items guardados antes de que el lote fuera
     * obligatorio). La validación bloqueante vive en el resolver, al momento de capturar.
     */
    private void verificarInvariante(List<MovimientoStockLote> creados,
                                     MovimientoStock movimiento,
                                     RecepcionMercaderiaItem item) {
        if (creados.isEmpty()) {
            return;
        }
        double sumaLotes = creados.stream()
                .mapToDouble(l -> l.getCantidad() != null ? l.getCantidad() : 0.0)
                .sum();
        double cantidadMovimiento = movimiento.getCantidad() != null ? movimiento.getCantidad() : 0.0;
        if (Math.abs(sumaLotes - cantidadMovimiento) > 0.0001) {
            log.warning("Desglose por lote inconsistente en recepcion item " + item.getId()
                    + ": suma de lotes = " + sumaLotes
                    + ", cantidad del movimiento = " + cantidadMovimiento);
        }
    }

    private MovimientoStockLote crearMovimiento(RecepcionMercaderiaItem item,
                                                MovimientoStock movimiento,
                                                String numeroLote,
                                                LocalDate fechaVencimiento,
                                                Double cantidad,
                                                Presentacion presentacion) {
        // Resolver (o crear) el lote en el maestro. Acá es donde dos recepciones del mismo lote
        // terminan apuntando a la misma fila y sumando en el mismo saldo, en vez de generar dos
        // lotes distintos por una diferencia de tipeo en la fecha.
        Proveedor proveedor = item.getRecepcionMercaderia() != null
                ? item.getRecepcionMercaderia().getProveedor()
                : null;
        Lote loteMaestro = loteService.obtenerOCrear(item.getProducto(), numeroLote, fechaVencimiento,
                proveedor, movimiento.getUsuario());

        MovimientoStockLote movimientoLote = new MovimientoStockLote();
        movimientoLote.setSucursalId(movimiento.getSucursalId());
        movimientoLote.setMovimientoStockId(movimiento.getId());
        movimientoLote.setLote(loteMaestro);
        movimientoLote.setProducto(item.getProducto());
        movimientoLote.setPresentacion(presentacion);
        // numero_lote queda desnormalizado a propósito: es inmutable y deja la fila legible en la
        // filial aunque el maestro todavía no haya replicado. La fecha de vencimiento ya NO se
        // escribe acá: su fuente de verdad es operaciones.lote.
        movimientoLote.setNumeroLote(numeroLote);
        movimientoLote.setCantidad(cantidad);
        movimientoLote.setReferencia(item.getId());
        movimientoLote.setEstado(true);
        movimientoLote.setUsuario(movimiento.getUsuario());
        movimientoLote.setCreadoEn(movimiento.getCreadoEn() != null ? movimiento.getCreadoEn() : LocalDateTime.now());
        return save(movimientoLote);
    }

    /**
     * Saldo por lote de un producto en una sucursal, ordenado por FEFO.
     */
    public List<StockLoteDto> stockPorLote(Long productoId, Long sucursalId) {
        return repository.stockPorLote(productoId, sucursalId);
    }

    public List<MovimientoStockLote> findByMovimientoStock(Long movimientoStockId, Long sucursalId) {
        return repository.findByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
    }

}
