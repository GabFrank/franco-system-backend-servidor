package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.LoteSinContarDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El desglose por lote del ajuste que produce la finalización de una toma de inventario.
 *
 * Hasta ahora {@code finalizarInventarioEnSucursal()} escribía SOLO el {@code MovimientoStock}
 * agregado. Para un producto con control de lote eso deja el ledger sin tocar, con lo que la
 * mercadería contada nunca vuelve a ser asignable por FEFO — el mismo agujero que
 * {@link AjusteStockLoteService} vino a cerrar para el ajuste manual.
 *
 * Contar por lote es, en el fondo, una ATRIBUCIÓN: el stock que estaba en el bucket sin trazar pasa
 * a tener dueño. Por eso el plan siempre incluye la fila sintética {@code SIN LOTE} con lo que las
 * filas por lote no explican.
 *
 * <pre>
 *   Existencia 50, lote L1 con saldo 30 (20 sin trazar). Se cuentan 28 de L1:
 *
 *   movimiento_stock         AJUSTE    -22      (28 − 50)
 *     movimiento_stock_lote  L1         -2      (28 − 30)
 *     movimiento_stock_lote  SIN LOTE  -20      (el resto: el bucket queda en cero)
 * </pre>
 *
 * La decisión —{@link #planificar}— es una función pura y se prueba sola, igual que
 * {@code AjusteStockLoteService.calcular()}. Lo de acá abajo solo la ejecuta.
 */
@Service
public class InventarioLoteService {

    /** Tolerancia al comparar cantidades en punto flotante, igual que en el resto del módulo. */
    static final double EPSILON = 0.0001;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;

    @Autowired
    private MovimientoStockLoteService movimientoStockLoteService;

    @Autowired
    private LoteService loteService;

    /**
     * Decide qué se escribe para un producto con control de lote.
     *
     * @param existencia      stock agregado del producto en la sucursal, en unidades base.
     * @param contadoTotal    TODO lo contado del producto en la toma, en unidades base — incluidos
     *                        los renglones SIN lote. No es la suma de {@code contadoPorLote}: un
     *                        producto con control de lote puede tener renglones sin atribuir, y esa
     *                        mercadería cuenta para la existencia aunque no pueda generar una fila
     *                        de lote. Sin este parámetro esas unidades desaparecían del ajuste.
     * @param saldosPorLote   saldo actual de cada lote REAL en esa sucursal, por id de lote. Las
     *                        filas {@code SIN LOTE} del ledger no van acá: no son stock atribuido.
     * @param contadoPorLote  lo contado en la toma, por id de lote, ya convertido a unidades base.
     */
    public static PlanConteoLote planificar(double existencia,
                                            double contadoTotal,
                                            Map<Long, Double> saldosPorLote,
                                            Map<Long, Double> contadoPorLote) {
        Map<Long, Double> saldos = saldosPorLote != null ? saldosPorLote : Collections.emptyMap();
        Map<Long, Double> contado = contadoPorLote != null ? contadoPorLote : Collections.emptyMap();

        // Sin un solo renglón contado no hay ajuste posible. "Nadie contó" no es "contaron cero":
        // tomarlo como cero le llevaría la existencia a cero a un producto que nadie miró.
        if (contado.isEmpty() && Math.abs(contadoTotal) <= EPSILON) {
            return PlanConteoLote.omitir(sinContar(saldos, contado));
        }

        List<Long> sinContar = sinContar(saldos, contado);
        if (!sinContar.isEmpty()) {
            return PlanConteoLote.omitir(sinContar);
        }

        double cantidadMovimiento = contadoTotal - existencia;

        Map<Long, Double> porLote = new LinkedHashMap<>();
        double sumaHijas = 0.0;
        for (Map.Entry<Long, Double> entrada : contado.entrySet()) {
            double contadoDelLote = entrada.getValue() != null ? entrada.getValue() : 0.0;
            // Un lote que el sistema no tenía arranca en cero: es mercadería que apareció en la
            // góndola y que hasta ahora vivía en el bucket sin trazar.
            Double saldo = saldos.get(entrada.getKey());
            double diferencia = contadoDelLote - (saldo != null ? saldo : 0.0);
            porLote.put(entrada.getKey(), diferencia);
            sumaHijas += diferencia;
        }

        return new PlanConteoLote(false, Collections.emptyList(), cantidadMovimiento, porLote,
                cantidadMovimiento - sumaHijas);
    }

    /**
     * Los lotes con saldo que ningún renglón contó.
     *
     * Un lote agotado no cuenta: no es mercadería que alguien debió encontrar en la góndola.
     * La lista sale ordenada para que el mensaje al operador sea estable.
     */
    private static List<Long> sinContar(Map<Long, Double> saldos, Map<Long, Double> contado) {
        List<Long> faltantes = new ArrayList<>();
        for (Map.Entry<Long, Double> entrada : saldos.entrySet()) {
            double saldo = entrada.getValue() != null ? entrada.getValue() : 0.0;
            if (Math.abs(saldo) > EPSILON && !contado.containsKey(entrada.getKey())) {
                faltantes.add(entrada.getKey());
            }
        }
        Collections.sort(faltantes);
        return faltantes;
    }

    /**
     * El saldo actual de cada lote REAL del producto en la sucursal.
     *
     * Las filas {@code SIN LOTE} del ledger quedan afuera: son el registro de lo que salió sin
     * trazar, no stock atribuido. Es el mismo criterio que usa
     * {@code AjusteStockLoteService.resumen()}.
     *
     * ⚠️ **Se descuentan las filas del propio ajuste de esta toma.** Al re-finalizar, el ledger
     * todavía tiene el desglose de la corrida anterior, así que el movimiento se estaría
     * descontando a sí mismo y el plan saldría calculado contra un stock que no existe. Es el
     * mismo cuidado que documenta {@code MovimientoStockLoteService.limpiarDesglose()}, pero acá no
     * alcanza con borrar primero: si el plan termina omitiendo el producto, no hay que haber
     * tocado nada.
     */
    public Map<Long, Double> saldosPorLote(Long productoId, Long sucursalId, MovimientoStock excluir) {
        Map<Long, Double> saldos = new HashMap<>();
        for (StockLoteDto fila : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            if (fila.getLoteId() != null) {
                saldos.put(fila.getLoteId(),
                        fila.getCantidadDisponible() != null ? fila.getCantidadDisponible() : 0.0);
            }
        }

        if (excluir != null && excluir.getId() != null) {
            for (MovimientoStockLote fila : movimientoStockLoteService
                    .findByMovimientoStock(excluir.getId(), excluir.getSucursalId())) {
                if (fila.getLote() == null || fila.getLote().getId() == null || fila.getCantidad() == null) {
                    // Las filas SIN LOTE no están en el mapa: no son stock atribuido a un lote.
                    continue;
                }
                saldos.merge(fila.getLote().getId(), -fila.getCantidad(), Double::sum);
            }
        }
        return saldos;
    }

    /**
     * Lo contado en toda la toma, por producto y por lote, en unidades base.
     *
     * Se recorre el inventario entero —no una zona— porque el mismo lote puede estar en góndola y
     * en depósito, y los dos conteos suman contra el mismo saldo.
     *
     * Los renglones sin lote no entran: el desglose solo puede hablar de lo que tiene lote. Lo que
     * esos renglones aportan al total ya está en el movimiento agregado, y la fila {@code SIN LOTE}
     * del plan se encarga del resto.
     */
    public Map<Long, Map<Long, Double>> contadoPorProductoYLote(Long inventarioId) {
        Map<Long, Map<Long, Double>> porProducto = new HashMap<>();

        for (InventarioProducto ip : inventarioProductoService.findByInventarioId(inventarioId)) {
            for (InventarioProductoItem ipi : inventarioProductoItemService
                    .findByInventarioProductoId(ip.getId())) {
                // Mismo criterio que la finalización: sin cantidad nadie contó, y saltear no es
                // tomar como cero.
                if (ipi.getCantidad() == null || ipi.getLote() == null
                        || ipi.getLote().getId() == null || ipi.getPresentacion() == null) {
                    continue;
                }
                Producto producto = ipi.getPresentacion().getProducto();
                if (producto == null || !Boolean.TRUE.equals(producto.getLote())) {
                    continue;
                }
                double enUnidades = ipi.getCantidad() * ipi.getPresentacion().getCantidad();
                porProducto
                        .computeIfAbsent(producto.getId(), id -> new HashMap<>())
                        .merge(ipi.getLote().getId(), enUnidades, Double::sum);
            }
        }
        return porProducto;
    }

    /**
     * Los lotes con saldo que ningún renglón de la toma contó.
     *
     * Existe para poder avisarlo ANTES de finalizar: la finalización deja esos productos enteros
     * fuera del ajuste, y sin este aviso el operador se entera después de cerrar la toma.
     *
     * Solo mira los productos que la toma incluye: un lote de un producto que nadie puso en el
     * conteo no es un lote sin contar, es un producto fuera del alcance.
     */
    public List<LoteSinContarDto> lotesSinContar(Long inventarioId) {
        Inventario inventario = inventarioService.findById(inventarioId).orElse(null);
        if (inventario == null || inventario.getSucursal() == null) {
            return Collections.emptyList();
        }
        Long sucursalId = inventario.getSucursal().getId();

        Map<Long, Map<Long, Double>> contado = contadoPorProductoYLote(inventarioId);
        List<LoteSinContarDto> faltantes = new ArrayList<>();

        for (Producto producto : productosConLoteDeLaToma(inventarioId)) {
            Map<Long, Double> contadoDelProducto =
                    contado.getOrDefault(producto.getId(), Collections.emptyMap());

            for (StockLoteDto fila : movimientoStockLoteService.stockPorLote(producto.getId(), sucursalId)) {
                double saldo = fila.getCantidadDisponible() != null ? fila.getCantidadDisponible() : 0.0;
                if (fila.getLoteId() == null || Math.abs(saldo) <= EPSILON
                        || contadoDelProducto.containsKey(fila.getLoteId())) {
                    continue;
                }
                faltantes.add(new LoteSinContarDto(fila.getLoteId(), fila.getNumeroLote(),
                        producto.getId(), producto.getDescripcion(), fila.getFechaVencimiento(),
                        fila.getFechaRetiro(), saldo));
            }
        }
        return faltantes;
    }

    /** Los productos con control de lote que la toma incluye, sin repetir. */
    private List<Producto> productosConLoteDeLaToma(Long inventarioId) {
        Map<Long, Producto> productos = new LinkedHashMap<>();
        for (InventarioProducto ip : inventarioProductoService.findByInventarioId(inventarioId)) {
            for (InventarioProductoItem ipi : inventarioProductoItemService
                    .findByInventarioProductoId(ip.getId())) {
                if (ipi.getPresentacion() == null) {
                    continue;
                }
                Producto producto = ipi.getPresentacion().getProducto();
                if (producto != null && Boolean.TRUE.equals(producto.getLote())) {
                    productos.putIfAbsent(producto.getId(), producto);
                }
            }
        }
        return new ArrayList<>(productos.values());
    }

    /**
     * Escribe el desglose por lote de un ajuste de finalización.
     *
     * ⚠️ **Borra primero las filas que ese movimiento ya tenía.** La finalización REUSA el
     * {@code MovimientoStock} de AJUSTE cuando la toma se vuelve a cerrar —lo busca por
     * (tipo, referencia, sucursal, producto) y le pisa la cantidad—, así que sin este borrado las
     * hijas viejas quedarían colgando y se sumarían a las nuevas. El padre se pisa; las hijas no,
     * porque son filas nuevas con su propia PK.
     */
    public void escribirDesglose(MovimientoStock movimiento, Producto producto,
                                 PlanConteoLote plan, Usuario usuario) {
        if (movimiento == null || movimiento.getId() == null || plan == null || plan.isOmitido()) {
            return;
        }

        movimientoStockLoteService.limpiarDesglose(movimiento);
        if (plan.isVacio()) {
            return;
        }

        List<MovimientoStockLote> filas = new ArrayList<>();
        for (Map.Entry<Long, Double> entrada : plan.getCantidadPorLote().entrySet()) {
            if (Math.abs(entrada.getValue()) <= EPSILON) {
                continue;
            }
            Lote lote = loteService.findById(entrada.getKey()).orElse(null);
            if (lote == null) {
                continue;
            }
            filas.add(crearFila(movimiento, producto, lote, lote.getNumeroLote(),
                    entrada.getValue(), usuario));
        }
        if (Math.abs(plan.getCantidadSinTrazar()) > EPSILON) {
            filas.add(crearFila(movimiento, producto, null, LoteFefoService.NUMERO_LOTE_SIN_TRAZAR,
                    plan.getCantidadSinTrazar(), usuario));
        }

        for (MovimientoStockLote fila : filas) {
            movimientoStockLoteService.save(fila);
        }
    }

    /** Misma forma que {@code AjusteStockLoteService.crearFila}: una hija nunca va suelta. */
    private MovimientoStockLote crearFila(MovimientoStock movimiento, Producto producto, Lote lote,
                                          String numeroLote, double cantidad, Usuario usuario) {
        MovimientoStockLote fila = new MovimientoStockLote();
        fila.setSucursalId(movimiento.getSucursalId());
        fila.setMovimientoStockId(movimiento.getId());
        fila.setLote(lote);
        fila.setProducto(producto);
        fila.setNumeroLote(numeroLote);
        fila.setFechaVencimiento(lote != null ? lote.getFechaVencimiento() : null);
        fila.setCantidad(cantidad);
        fila.setReferencia(producto.getId());
        fila.setEstado(true);
        fila.setUsuario(usuario);
        fila.setCreadoEn(movimiento.getCreadoEn());
        return fila;
    }
}
