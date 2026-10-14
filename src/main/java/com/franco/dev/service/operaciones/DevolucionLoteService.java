package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.DevolucionItem;
import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.personas.Proveedor;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stock por lote de las devoluciones.
 *
 * {@link DevolucionService} mueve la existencia agregada; este servicio anota el desglose por
 * lote de esos mismos movimientos, para los productos con control de lote. Sin esto la baja de
 * una devolucion no llegaba al ledger ({@code movimiento_stock_lote}) y el saldo por lote
 * quedaba por encima de lo que realmente habia.
 *
 * Tres reglas:
 * <ul>
 *   <li><b>La baja sale del lote que eligio el operador, entera.</b> No se recorta al saldo ni
 *       se descarta si el lote esta bloqueado: quien devuelve tiene ese lote en la mano, y un
 *       lote en recall es justamente lo que se devuelve. Sin lote elegido se reparte por FEFO,
 *       y si el saldo por lote no alcanza se desglosa lo que hay (el agregado manda).</li>
 *   <li><b>Deshacer espeja, no recalcula.</b> Revertir la baja o el canje copia el desglose del
 *       movimiento original con el signo contrario. Por eso el item guarda los ids de esos
 *       movimientos: buscarlos por tipo y referencia es ambiguo, porque la baja, su reversion,
 *       el canje y la reversion del canje comparten los dos.</li>
 *   <li><b>El canje entra al lote que entrega el proveedor</b>, que se crea si no existe.</li>
 * </ul>
 *
 * Vive aparte de {@link DevolucionService} para poder leer y probar estas reglas de un saque.
 */
@Service
@AllArgsConstructor
public class DevolucionLoteService {

    private final LoteFefoService loteFefoService;
    private final MovimientoStockLoteService movimientoStockLoteService;
    private final LoteService loteService;

    /** El producto del item lleva control de lote. Para el resto, nada de esto aplica. */
    public static boolean llevaLote(DevolucionItem item) {
        return item != null && item.getProducto() != null
                && Boolean.TRUE.equals(item.getProducto().getLote());
    }

    /** Desglosa por lote la baja de stock de un item al separar. */
    @Transactional
    public List<MovimientoStockLote> desglosarBaja(MovimientoStock baja, DevolucionItem item) {
        if (baja == null || baja.getId() == null || baja.getCantidad() == null || !llevaLote(item)) {
            return Collections.emptyList();
        }
        Producto producto = item.getProducto();
        double cantidad = Math.abs(baja.getCantidad());
        List<LoteFefoService.AsignacionLote> asignaciones;
        Lote elegido = item.getLoteMaestro();
        if (elegido != null) {
            asignaciones = Collections.singletonList(
                    new LoteFefoService.AsignacionLote(elegido.getId(), elegido.getNumeroLote(), cantidad));
        } else {
            asignaciones = loteFefoService.asignar(producto.getId(), baja.getSucursalId(), cantidad);
        }
        return movimientoStockLoteService.reemplazarDesglose(baja, producto, asignaciones, item.getId(), -1);
    }

    /**
     * Anota en {@code nuevo} el desglose de {@code movimientoOrigenId} con el signo dado: lo que
     * salio de cada lote vuelve a ese lote, y al reves. Si el original no tiene desglose (se
     * hizo antes de que las devoluciones llevaran lote) no anota nada.
     *
     * @param sucursalOrigenId sucursal del movimiento original; la PK del movimiento la incluye.
     * @param signo            1 si {@code nuevo} es una entrada, -1 si es una salida.
     */
    @Transactional
    public List<MovimientoStockLote> espejar(MovimientoStock nuevo, Long movimientoOrigenId,
                                             Long sucursalOrigenId, DevolucionItem item, int signo) {
        if (nuevo == null || nuevo.getId() == null || movimientoOrigenId == null || !llevaLote(item)) {
            return Collections.emptyList();
        }
        List<LoteFefoService.AsignacionLote> asignaciones = new ArrayList<>();
        for (MovimientoStockLote fila : movimientoStockLoteService
                .findByMovimientoStock(movimientoOrigenId, sucursalOrigenId)) {
            if (fila.getLote() == null || fila.getCantidad() == null) {
                continue;
            }
            asignaciones.add(new LoteFefoService.AsignacionLote(
                    fila.getLote().getId(), fila.getNumeroLote(), Math.abs(fila.getCantidad())));
        }
        return movimientoStockLoteService.reemplazarDesglose(
                nuevo, item.getProducto(), asignaciones, item.getId(), signo);
    }

    /**
     * Falta el lote del reingreso. Devuelve null si no hace falta o ya esta; el mensaje si falta.
     * Se pregunta antes de mover stock: sin numero no hay a que lote anotar la entrada.
     */
    public static String validarLoteReingreso(DevolucionItem item) {
        if (!llevaLote(item)) {
            return null;
        }
        Double reingresada = item.getCantidadReingresada();
        if (reingresada == null || reingresada <= 0) {
            return null;
        }
        if (LoteService.normalizarNumeroLote(item.getLoteReingreso()) == null) {
            return "Indique el numero de lote con el que reingresa " + item.getProducto().getDescripcion();
        }
        return null;
    }

    /**
     * Anota la entrada del canje en el lote que entrega el proveedor, creandolo si no existe.
     * Si el lote ya existia, sus fechas no se pisan: manda el maestro.
     */
    @Transactional
    public List<MovimientoStockLote> desglosarCanje(MovimientoStock entrada, DevolucionItem item,
                                                    Proveedor proveedor, Usuario usuario) {
        if (entrada == null || entrada.getId() == null || entrada.getCantidad() == null || !llevaLote(item)) {
            return Collections.emptyList();
        }
        Lote lote = loteService.obtenerOCrear(item.getProducto(), item.getLoteReingreso(),
                item.getVencimientoReingreso(), null, proveedor, usuario);
        if (lote == null) {
            return Collections.emptyList();
        }
        return movimientoStockLoteService.reemplazarDesglose(entrada, item.getProducto(),
                Collections.singletonList(new LoteFefoService.AsignacionLote(
                        lote.getId(), lote.getNumeroLote(), Math.abs(entrada.getCantidad()))),
                item.getId(), 1);
    }
}
