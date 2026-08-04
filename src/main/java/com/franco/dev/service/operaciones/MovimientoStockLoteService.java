package com.franco.dev.service.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItem;
import com.franco.dev.domain.operaciones.RecepcionMercaderiaItemVariacion;
import com.franco.dev.domain.operaciones.dto.MovimientoLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLotePresentacionDto;
import com.franco.dev.domain.operaciones.dto.StockLoteProjection;
import com.franco.dev.domain.operaciones.dto.StockLoteSucursalDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.productos.PresentacionService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ledger de stock por lote. Ver {@link MovimientoStockLote} para el racional del diseño.
 */
@Service
@AllArgsConstructor
public class MovimientoStockLoteService
        extends CrudService<MovimientoStockLote, MovimientoStockLoteRepository, EmbebedPrimaryKey> {

    private final MovimientoStockLoteRepository repository;

    private final LoteService loteService;

    private final PresentacionService presentacionService;

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
        creados.add(crearMovimiento(item, movimiento, numeroLote, item.getVencimientoRecibido(),
                item.getFechaRetiro(), cantidad, item.getPresentacionRecibida()));
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
            // Sin fecha de retiro manual: cada variación es un lote distinto con su propio
            // vencimiento, así que la del ítem no aplica y se deja derivar por lote.
            creados.add(crearMovimiento(item, movimiento, numeroLote, vencimiento, null, cantidad,
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
                                                LocalDate fechaRetiro,
                                                Double cantidad,
                                                Presentacion presentacion) {
        // Resolver (o crear) el lote en el maestro. Acá es donde dos recepciones del mismo lote
        // terminan apuntando a la misma fila y sumando en el mismo saldo, en vez de generar dos
        // lotes distintos por una diferencia de tipeo en la fecha.
        Proveedor proveedor = item.getRecepcionMercaderia() != null
                ? item.getRecepcionMercaderia().getProveedor()
                : null;
        Lote loteMaestro = loteService.obtenerOCrear(item.getProducto(), numeroLote, fechaVencimiento,
                fechaRetiro, proveedor, movimiento.getUsuario());

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

    /**
     * Saldo por lote expresado en una presentacion concreta, ordenado por FEFO.
     *
     * Es lo que consume la eleccion manual de lotes en transferencias: el operador carga en
     * cajas o packs, no en unidades, asi que la conversion se resuelve aca y no en la pantalla.
     * Con presentacionId nulo devuelve las cantidades en unidades, sin convertir.
     */
    public Page<StockLotePresentacionDto> stockPorLoteEnPresentacion(Long productoId, Long sucursalId,
                                                                     Long presentacionId, String numeroLote,
                                                                     Pageable pageable) {
        Presentacion presentacion = presentacionId != null
                ? presentacionService.findById(presentacionId).orElse(null)
                : null;
        double unidadesPorPresentacion = ConversionPresentacion.unidadesPorPresentacion(presentacion);
        String descripcion = presentacion != null ? presentacion.getDescripcion() : null;

        // Se apoya en buscarStockPorLote, que ya pagina, ordena por FEFO y filtra por numero de
        // lote. Aca solo se agrega la conversion a la presentacion del operador.
        return buscarStockPorLote(productoId, sucursalId, null, null, numeroLote, null, null, pageable)
                .map(lote -> new StockLotePresentacionDto(
                        lote.getLoteId(),
                        lote.getNumeroLote(),
                        lote.getFechaVencimiento(),
                        lote.getFechaRetiro(),
                        lote.getEstado(),
                        lote.getCantidadDisponible(),
                        // Completas, no la division: no se puede transferir una fraccion de caja.
                        ConversionPresentacion.presentacionesCompletas(
                                lote.getCantidadDisponible(), unidadesPorPresentacion),
                        ConversionPresentacion.unidadesSobrantes(
                                lote.getCantidadDisponible(), unidadesPorPresentacion),
                        unidadesPorPresentacion,
                        descripcion
                ));
    }

    public List<MovimientoStockLote> findByMovimientoStock(Long movimientoStockId, Long sucursalId) {
        return repository.findByMovimientoStockIdAndSucursalId(movimientoStockId, sucursalId);
    }

    /**
     * Borra el desglose de un movimiento y lo confirma contra la base.
     *
     * Hay que llamarlo ANTES de calcular una asignacion nueva para ese mismo movimiento. El saldo
     * por lote se deriva del ledger, y el ledger todavia contiene las filas de la corrida
     * anterior: sin este borrado previo, el movimiento se descuenta a si mismo del saldo y la
     * asignacion se calcula contra un stock que no existe.
     *
     * El caso concreto: una transferencia recalcula su desglose en cada etapa (preparacion,
     * transporte, recepcion). En la segunda corrida, un lote de 88 unidades del que ya se habian
     * tomado 65 aparecia con 23 disponibles, y la eleccion del operador se recortaba a esas 23.
     */
    @Transactional
    public void limpiarDesglose(MovimientoStock movimiento) {
        if (movimiento == null || movimiento.getId() == null) {
            return;
        }
        repository.deleteByMovimientoStockIdAndSucursalId(
                movimiento.getId(), movimiento.getSucursalId());
        // Flush explicito: la consulta de saldo tiene que ver la base ya sin estas filas.
        repository.flush();
    }

    /**
     * Reemplaza el desglose por lote de un movimiento con las asignaciones dadas.
     *
     * Se borra y se vuelve a crear en vez de intentar un diff porque una transferencia corrige la
     * cantidad varias veces (preparación, transporte, recepción) y el reparto entre lotes puede
     * cambiar por completo entre etapas.
     *
     * @param signo 1 para entradas, -1 para salidas.
     */
    @Transactional
    public List<MovimientoStockLote> reemplazarDesglose(MovimientoStock movimiento,
                                                        Producto producto,
                                                        List<LoteFefoService.AsignacionLote> asignaciones,
                                                        Long referencia,
                                                        int signo) {
        List<MovimientoStockLote> creados = new ArrayList<>();
        if (movimiento == null || movimiento.getId() == null || producto == null) {
            return creados;
        }

        repository.deleteByMovimientoStockIdAndSucursalId(movimiento.getId(), movimiento.getSucursalId());

        if (asignaciones == null || asignaciones.isEmpty()) {
            return creados;
        }

        for (LoteFefoService.AsignacionLote asignacion : asignaciones) {
            if (asignacion.getCantidad() == null || asignacion.getCantidad() <= 0) {
                continue;
            }
            Lote loteMaestro = loteService.findById(asignacion.getLoteId()).orElse(null);
            if (loteMaestro == null) {
                log.warning("Asignacion FEFO con lote inexistente " + asignacion.getLoteId()
                        + ": no se desglosa esa cantidad.");
                continue;
            }

            MovimientoStockLote fila = new MovimientoStockLote();
            fila.setSucursalId(movimiento.getSucursalId());
            fila.setMovimientoStockId(movimiento.getId());
            fila.setLote(loteMaestro);
            fila.setProducto(producto);
            fila.setNumeroLote(loteMaestro.getNumeroLote());
            fila.setCantidad(asignacion.getCantidad() * signo);
            fila.setReferencia(referencia);
            fila.setEstado(movimiento.getEstado() != null ? movimiento.getEstado() : true);
            fila.setUsuario(movimiento.getUsuario());
            fila.setCreadoEn(movimiento.getCreadoEn() != null ? movimiento.getCreadoEn() : LocalDateTime.now());
            creados.add(save(fila));
        }
        return creados;
    }

    /**
     * Propaga el estado del movimiento agregado a su desglose por lote.
     *
     * Hace falta porque no todos los flujos borran: las transferencias canceladas o rechazadas y
     * las ventas anuladas usan borrado lógico ({@code estado = false}), y ahí el ON DELETE CASCADE
     * de la FK no se dispara.
     */
    @Transactional
    public void sincronizarEstado(MovimientoStock movimiento) {
        if (movimiento == null || movimiento.getId() == null) {
            return;
        }
        boolean estado = movimiento.getEstado() != null && movimiento.getEstado();
        List<MovimientoStockLote> filas = repository.findByMovimientoStockIdAndSucursalId(
                movimiento.getId(), movimiento.getSucursalId());
        for (MovimientoStockLote fila : filas) {
            if (fila.getEstado() == null || fila.getEstado() != estado) {
                fila.setEstado(estado);
                super.save(fila);
            }
        }
    }

    /**
     * Consulta general de stock por lote con filtros opcionales, para la pantalla
     * "Stock por lotes". Responde "¿dónde tengo qué?".
     *
     * Los filtros vacíos se normalizan a null para desactivarlos. El estado se recibe como enum
     * y se pasa como texto porque la consulta es nativa.
     */
    public Page<StockLoteDto> buscarStockPorLote(Long productoId, Long sucursalId, Long proveedorId,
                                                  EstadoLote estado, String numeroLote, String texto,
                                                  String vencimientoHasta, Pageable pageable) {
        return repository.buscarStockPorLote(
                productoId,
                sucursalId,
                proveedorId,
                estado != null ? estado.name() : null,
                normalizarFiltro(numeroLote),
                normalizarFiltro(texto),
                normalizarFiltro(vencimientoHasta),
                pageable
        ).map(this::aDto);
    }

    /**
     * Desglose por sucursal del saldo de un lote, con las sucursales sin stock en 0.
     */
    public List<StockLoteSucursalDto> stockLotePorSucursal(Long loteId) {
        if (loteId == null) {
            return Collections.emptyList();
        }
        return repository.stockLotePorSucursal(loteId).stream()
                .map(p -> new StockLoteSucursalDto(
                        p.getSucursalId(),
                        p.getSucursalNombre(),
                        p.getCantidadDisponible() != null ? p.getCantidadDisponible() : 0d))
                .collect(Collectors.toList());
    }

    /**
     * Historial de un lote, del movimiento más reciente al más viejo.
     *
     * Con sucursalId nulo devuelve el recorrido completo del lote por toda la red, que es lo que
     * hace falta en un recall: la compra que lo trajo, las transferencias que lo repartieron y
     * las ventas que lo sacaron.
     */
    public Page<MovimientoLoteDto> movimientosPorLote(Long loteId, Long sucursalId, Pageable pageable) {
        if (loteId == null) {
            return Page.empty(pageable);
        }
        return repository.movimientosPorLote(loteId, sucursalId, pageable)
                .map(p -> new MovimientoLoteDto(
                        p.getId(),
                        p.getSucursalId(),
                        p.getFecha() != null ? p.getFecha().toLocalDateTime() : null,
                        p.getSucursalNombre(),
                        p.getTipoMovimiento(),
                        p.getReferencia(),
                        p.getCantidad() != null ? p.getCantidad() : 0d,
                        p.getUsuarioNombre()));
    }

    /**
     * La consulta agrupa por lote, no por lote y sucursal, así que sucursalId y sucursalNombre
     * quedan sin resolver: el desglose se pide aparte con {@link #stockLotePorSucursal(Long)}.
     */
    private StockLoteDto aDto(StockLoteProjection p) {
        return new StockLoteDto(
                p.getLoteId(),
                p.getProductoId(),
                p.getProductoDescripcion(),
                null,
                null,
                p.getNumeroLote(),
                p.getFechaVencimiento(),
                p.getFechaRetiro(),
                p.getEstado() != null ? EstadoLote.valueOf(p.getEstado()) : null,
                p.getCantidadDisponible(),
                p.getProveedorNombre()
        );
    }

    private String normalizarFiltro(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

}
