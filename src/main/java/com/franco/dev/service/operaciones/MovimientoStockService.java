package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.dto.StockPorTipoMovimientoDto;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.dto.StockPorSucursalDto;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.TransferenciaItemLote;
import com.franco.dev.domain.operaciones.dto.ProductoSaldoDto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.MovimientoStockRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.empresarial.SucursalService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MovimientoStockService extends CrudService<MovimientoStock, MovimientoStockRepository, EmbebedPrimaryKey> {
    private final MovimientoStockRepository repository;

    @Autowired
    private final ModificacionService modificacionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private MovimientoStockLoteService movimientoStockLoteService;

    @Autowired
    private LoteFefoService loteFefoService;

    @Autowired
    private TransferenciaItemLoteService transferenciaItemLoteService;

    @Override
    public MovimientoStockRepository getRepository() {
        return repository;
    }

    public Double stockByProductoIdAndSucursalId(Long proId, Long sucId) {
        Float stock = repository.stockByProductoIdAndSucursalId(proId, sucId);
        if (stock == null)
            stock = Float.valueOf(0);
        return Double.valueOf(stock);
    }

    /**
     * Existencia del producto en cada sucursal, en una sola consulta.
     *
     * Las sucursales sin movimientos no vienen en el resultado: no hay filas
     * que sumar. El llamador decide como mostrarlas — cero, normalmente.
     */
    public List<StockPorSucursalDto> stockPorSucursales(Long proId) {
        return repository.stockPorSucursales(proId);
    }

    public Double stockByProductoId(Long proId) {
        Double finalStock = 0.0;
        List<Sucursal> sucursalList = sucursalService.findAll2();
        for (Sucursal s : sucursalList) {
            finalStock += stockByProductoIdAndSucursalId(proId, s.getId());
        }
        return finalStock;
    }

    public Double stockByProductoIdExcluyendoNombresSucursal(Long proId, List<String> nombresExcluidos) {
        Double finalStock = 0.0;
        List<Sucursal> sucursalList = sucursalService.findAll2();
        for (Sucursal s : sucursalList) {
            if (nombresExcluidos != null && nombresExcluidos.stream()
                    .anyMatch(nombre -> nombre.equalsIgnoreCase(s.getNombre()))) {
                continue;
            }
            finalStock += stockByProductoIdAndSucursalId(proId, s.getId());
        }
        return finalStock;
    }

    public Double stockByProductoIdExecptMovStockId(Long proId, Long movId, Long sucId) {
        Float stock = repository.stockByProductoIdExeptMovimientoId(proId, movId, sucId);
        return Double.valueOf(stock != null ? stock : 0);
    }

    public Double stockByProductoIdAndSucursalIdAntesDeFecha(Long proId, Long sucId, LocalDateTime fecha) {
        Float stock = repository.stockByProductoIdAndSucursalIdAntesDeFecha(proId, sucId, fecha);
        return Double.valueOf(stock != null ? stock : 0);
    }

    public Page<MovimientoStock> findMovimientoStockWithFilters(LocalDateTime inicio,
            LocalDateTime fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId,
            Pageable pageable) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        return repository.findByFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId, pageable);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MovimientoStock save(MovimientoStock entity) {
        boolean esNuevo = entity.getId() == null;
        if (esNuevo) {
            entity.setCreadoEn(LocalDateTime.now());
            Long newId = Long.valueOf(1);
            Long lastId = repository.findMaxId(entity.getSucursalId());
            if (lastId == null)
                lastId = Long.valueOf(0);
            if (lastId % 2 != 0) {
                newId = lastId + 2;
            } else {
                newId = lastId + 1;
            }
            entity.setId(newId);
        }
        MovimientoStock e = super.save(entity);

        if (e != null && e.getTipoMovimiento() == TipoMovimiento.AJUSTE) {
            try {
                if (esNuevo) {
                    modificacionService.registrarInsercion(e, "AJUSTE_STOCK", "operaciones", "movimiento_stock");
                } else {
                    modificacionService.registrarActualizacion(entity, e, "AJUSTE_STOCK", "operaciones",
                            "movimiento_stock");
                }
            } catch (Exception ex) {
                log.warning("Error registrando auditoría de ajuste de stock: " + ex.getMessage());
            }
        }

        return e;
    }

    @Override
    public Boolean delete(MovimientoStock entity) {
        Boolean ok = super.delete(entity);
        return ok;
    }

    public List<MovimientoStock> ultimosMovimientos(Long proId, TipoMovimiento tm, Integer limit) {
        if (tm == null) {
            tm = TipoMovimiento.COMPRA;
        }
        if (limit < 1) {
            limit = 1;
        }
        return repository.ultimosMovimientosPorProductoId(proId, tm.toString(), limit);
    }

    public List<MovimientoStock> findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento tipoMovimiento,
            Long referencia, Long sucId) {
        return repository.findByTipoMovimientoAndReferenciaAndSucursalId(tipoMovimiento, referencia, sucId);
    }

    public MovimientoStock findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento tipoMovimiento,
            Long referencia, Long sucId, Long proId) {
        List<MovimientoStock> movimientoStockList = repository
                .findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(tipoMovimiento, referencia, sucId, proId);
        if (movimientoStockList == null || movimientoStockList.isEmpty()) {
            return null;
        }
        if (movimientoStockList.size() > 1) {
            // Delete duplicates, keep the first one
            for (int i = 1; i < movimientoStockList.size(); i++) {
                try {
                    repository.delete(movimientoStockList.get(i));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return movimientoStockList.get(0);
    }

    public List<MovimientoStock> findListByTipoMovimientoAndReferenciaEstadoTrue(TipoMovimiento tipoMovimiento,
            Long referencia) {
        return repository.findByTipoMovimientoAndReferenciaAndEstadoTrue(tipoMovimiento, referencia);
    }

    public List<MovimientoStock> findByDate(String inicio, String fin) {
        return repository.findByDate(inicio, fin);
    }

    public Double findStockWithFilters(LocalDateTime inicio,
            LocalDateTime fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        Double stock = repository.findStockWithFilters(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
        return stock == null ? 0 : stock;
    }

    public List<StockPorTipoMovimientoDto> findStockPorTipoMovimiento(LocalDateTime inicio,
            LocalDateTime fin,
            List<Long> sucursalList,
            Long productoId,
            List<TipoMovimiento> tipoMovimientoList,
            Long usuarioId) {
        List<String> stringEnum = null;
        if (tipoMovimientoList != null) {
            stringEnum = tipoMovimientoList.stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }
        return repository.findStockPorTipoMovimiento(inicio, fin, sucursalList, productoId, stringEnum, usuarioId);
    }

    @Transactional
    public List<MovimientoStock> createMovimientoFromTransferenciaItem(TransferenciaItem e) {
        TransferenciaItem finalE = e;
        MovimientoStock movimientoStockSalida = findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                TipoMovimiento.TRANSFERENCIA,
                finalE.getId(),
                finalE.getTransferencia().getSucursalOrigen().getId(),
                finalE.getPresentacionPreTransferencia().getProducto().getId());
        MovimientoStock movimientoStockEntrada = findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(
                TipoMovimiento.TRANSFERENCIA,
                finalE.getId(),
                finalE.getTransferencia().getSucursalDestino().getId(),
                finalE.getPresentacionPreTransferencia().getProducto().getId());
        Boolean esRechazado = false;
        esRechazado = e.getMotivoRechazoPreparacion() != null || e.getMotivoRechazoPreTransferencia() != null
                || e.getMotivoRechazoRecepcion() != null || e.getMotivoRechazoTransporte() != null;
        MovimientoStock ms = null;

        switch (e.getTransferencia().getEtapa()) {
            case PREPARACION_MERCADERIA:
                ms = movimientoStockSalida != null ? movimientoStockSalida : new MovimientoStock();
                ms.setEstado(!esRechazado);
                ms.setSucursalId(e.getTransferencia().getSucursalOrigen().getId());
                ms.setCantidad(e.getCantidadPreparacion() * e.getPresentacionPreparacion().getCantidad() * -1);
                ms.setProducto(e.getPresentacionPreparacion().getProducto());
                ms.setReferencia(e.getId());
                ms.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                ms.setCreadoEn(LocalDateTime.now());
                ms.setUsuario(e.getUsuario());
                movimientoStockSalida = save(ms);
                break;
            case RECEPCION_EN_VERIFICACION:
                ms = movimientoStockEntrada != null ? movimientoStockEntrada : new MovimientoStock();
                ms.setEstado(!esRechazado);
                ms.setSucursalId(e.getTransferencia().getSucursalDestino().getId());
                ms.setCantidad(e.getCantidadRecepcion() * e.getPresentacionRecepcion().getCantidad());
                ms.setProducto(e.getPresentacionRecepcion().getProducto());
                ms.setReferencia(e.getId());
                ms.setTipoMovimiento(TipoMovimiento.TRANSFERENCIA);
                ms.setCreadoEn(LocalDateTime.now());
                ms.setUsuario(e.getUsuario());
                movimientoStockEntrada = save(ms);
                ms = movimientoStockSalida;
                ms.setEstado(!esRechazado);
                movimientoStockSalida = save(ms);
                break;
            case TRANSPORTE_VERIFICACION:
                if (movimientoStockSalida != null) {
                    ms = movimientoStockSalida;
                    ms.setCantidad(e.getCantidadTransporte() * e.getPresentacionTransporte().getCantidad() * -1);
                    ms.setProducto(e.getPresentacionTransporte().getProducto());
                    ms.setEstado(!esRechazado);
                    movimientoStockSalida = save(ms);
                }
                break;
        }

        if (e.getTransferencia().getEstado() == TransferenciaEstado.CANCELADA) {
            if (movimientoStockSalida != null) {
                movimientoStockSalida.setEstado(false);
                save(movimientoStockSalida);
            }
            if (movimientoStockEntrada != null) {
                movimientoStockEntrada.setEstado(false);
                save(movimientoStockEntrada);
            }
        }

        // Desglose por lote. Solo para productos con control de lote: el resto del flujo queda
        // exactamente igual que antes.
        desglosarTransferenciaPorLote(e, movimientoStockSalida, movimientoStockEntrada);

        List<MovimientoStock> res = new ArrayList<>();
        res.add(movimientoStockSalida);
        res.add(movimientoStockEntrada);
        return res;
    }

    /**
     * Mantiene el desglose por lote de una transferencia alineado con sus movimientos agregados.
     *
     * La salida en la sucursal origen se resuelve por FEFO. La entrada en destino replica
     * exactamente los mismos lotes que salieron: el lote viaja con la mercadería, no se crea uno
     * nuevo. Eso sale gratis del diseño, porque operaciones.lote no tiene sucursal — transferir es
     * mover cantidad de (lote, origen) a (lote, destino).
     *
     * Se recalcula en cada etapa porque las cantidades cambian (preparación, transporte,
     * recepción) y puede haber rechazos parciales.
     */
    private void desglosarTransferenciaPorLote(TransferenciaItem item,
                                               MovimientoStock salida,
                                               MovimientoStock entrada) {
        Producto producto = item.getPresentacionPreTransferencia() != null
                ? item.getPresentacionPreTransferencia().getProducto()
                : null;
        if (producto == null || !Boolean.TRUE.equals(producto.getLote())) {
            return;
        }

        // Salida: primero los lotes que eligio el operador, el faltante por FEFO.
        if (salida != null && salida.getId() != null) {
            if (Boolean.TRUE.equals(salida.getEstado()) && salida.getCantidad() != null
                    && salida.getCantidad() < 0) {
                double cantidadSalida = Math.abs(salida.getCantidad());
                // Primero se borra el desglose anterior de ESTE movimiento: el saldo por lote sale
                // del ledger, y si las filas viejas siguen ahí el movimiento se descuenta a sí
                // mismo y la asignación se calcula contra un stock que en realidad está libre.
                movimientoStockLoteService.limpiarDesglose(salida);
                List<LoteFefoService.AsignacionLote> preferencias = preferenciasDeLote(item);
                List<LoteFefoService.AsignacionLote> asignaciones = loteFefoService.asignarConPreferencia(
                        producto.getId(), salida.getSucursalId(), cantidadSalida, preferencias);
                advertirSiSeCompletoPorFefo(item, preferencias, asignaciones);
                movimientoStockLoteService.reemplazarDesglose(
                        salida, producto, asignaciones, item.getId(), -1);
            } else {
                movimientoStockLoteService.sincronizarEstado(salida);
            }
        }

        // Entrada: los mismos lotes que salieron, recortados a la cantidad efectivamente recibida.
        if (entrada != null && entrada.getId() != null) {
            if (Boolean.TRUE.equals(entrada.getEstado()) && entrada.getCantidad() != null
                    && entrada.getCantidad() > 0 && salida != null && salida.getId() != null) {
                List<LoteFefoService.AsignacionLote> recibidos = recortarAsignaciones(
                        movimientoStockLoteService.findByMovimientoStock(
                                salida.getId(), salida.getSucursalId()),
                        entrada.getCantidad());
                movimientoStockLoteService.reemplazarDesglose(
                        entrada, producto, recibidos, item.getId(), 1);
            } else {
                movimientoStockLoteService.sincronizarEstado(entrada);
            }
        }
    }

    /**
     * Lotes que el operador eligió a mano para este ítem, en orden de prioridad.
     *
     * Devuelve vacío cuando no eligió ninguno, que es el caso de toda transferencia anterior a
     * esta funcionalidad y de todo producto donde el operador no toca nada. Con la lista vacía,
     * la asignación queda en FEFO puro, igual que antes.
     */
    private List<LoteFefoService.AsignacionLote> preferenciasDeLote(TransferenciaItem item) {
        List<LoteFefoService.AsignacionLote> preferencias = new ArrayList<>();
        if (item == null || item.getId() == null) {
            return preferencias;
        }
        for (TransferenciaItemLote fila : transferenciaItemLoteService.asignacionVigente(item.getId())) {
            if (fila.getLote() == null || fila.getLote().getId() == null) {
                continue;
            }
            preferencias.add(new LoteFefoService.AsignacionLote(
                    fila.getLote().getId(), fila.getNumeroLote(), fila.getCantidad()));
        }
        return preferencias;
    }

    /**
     * Deja rastro cuando la elección manual no alcanzó y hubo que completar por FEFO.
     *
     * No corta la operación a propósito: el criterio del sistema es que el stock agregado manda y
     * la mercadería igual sale. El aviso al operador lo da la pantalla antes de guardar; esto es
     * la red de seguridad para poder auditarlo después.
     */
    private void advertirSiSeCompletoPorFefo(TransferenciaItem item,
                                             List<LoteFefoService.AsignacionLote> preferencias,
                                             List<LoteFefoService.AsignacionLote> asignaciones) {
        if (preferencias.isEmpty() || asignaciones == null) {
            return;
        }
        List<Long> preferidos = preferencias.stream()
                .map(LoteFefoService.AsignacionLote::getLoteId)
                .collect(Collectors.toList());
        boolean completadoPorFefo = asignaciones.stream()
                .anyMatch(a -> a.getLoteId() != null && !preferidos.contains(a.getLoteId()));
        if (completadoPorFefo) {
            log.warning("Transferencia item " + item.getId() + ": los lotes elegidos a mano no "
                    + "cubrieron la cantidad y el resto se completo por FEFO.");
        }
    }

    /**
     * Recorta el desglose de la salida a la cantidad realmente recibida en destino, respetando el
     * orden FEFO en el que salió. Si en el camino se rechaza mercadería, lo que no llegó es lo
     * último de la lista: los lotes de vencimiento más lejano.
     */
    private List<LoteFefoService.AsignacionLote> recortarAsignaciones(
            List<com.franco.dev.domain.operaciones.MovimientoStockLote> filasSalida,
            Double cantidadRecibida) {
        List<LoteFefoService.AsignacionLote> resultado = new ArrayList<>();
        if (filasSalida == null || cantidadRecibida == null || cantidadRecibida <= 0) {
            return resultado;
        }
        double pendiente = cantidadRecibida;
        for (com.franco.dev.domain.operaciones.MovimientoStockLote fila : filasSalida) {
            if (pendiente <= 0.0001) break;
            if (fila.getLote() == null || fila.getCantidad() == null) continue;
            double disponible = Math.abs(fila.getCantidad());
            if (disponible <= 0) continue;
            double aTomar = Math.min(disponible, pendiente);
            resultado.add(new LoteFefoService.AsignacionLote(
                    fila.getLote().getId(), fila.getNumeroLote(), aTomar));
            pendiente -= aTomar;
        }
        return resultado;
    }

    public Page<ProductoSaldoDto> findProductosConCantidadPositiva(Long sucursalId, Long productoId,
            Pageable pageable) {
        return repository.findProductosConCantidadPositiva(sucursalId, productoId, pageable);
    }

    public Page<ProductoSaldoDto> findProductosConCantidadNegativa(Long sucursalId, Long productoId,
            Pageable pageable) {
        return repository.findProductosConCantidadNegativa(sucursalId, productoId, pageable);
    }

    public Page<ProductoSaldoDto> findProductosFaltantes(Long sucursalId, Long productoId, LocalDateTime fechaInicio,
            LocalDateTime fechaFin, Pageable pageable) {
        return repository.findProductosFaltantes(sucursalId, productoId, fechaInicio, fechaFin, pageable);
    }

}