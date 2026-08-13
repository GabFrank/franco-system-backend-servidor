package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.AjusteStockLoteResultadoDto;
import com.franco.dev.domain.operaciones.dto.ResumenStockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.ModoAjusteLote;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.operaciones.input.AjusteStockLoteInput;
import com.franco.dev.service.configuraciones.ModificacionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import lombok.Data;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ajuste de stock de un producto con control de lote.
 *
 * Es la puerta que faltaba: hasta ahora el ledger por lote solo se escribía desde la compra, la
 * venta y la transferencia, así que cualquier corrección manual de stock entraba en la existencia
 * agregada y no en el desglose por lote. Cada una de esas correcciones dejaba stock que FEFO no
 * podía asignar nunca más.
 *
 * INVARIANTE, igual que en el resto del módulo: se escribe SIEMPRE un movimiento agregado padre y
 * N filas hijas que suman exactamente su cantidad. Nunca una fila hija suelta.
 *
 * Las dos operaciones difieren solo en si el total cambia:
 *
 * <pre>
 *   CORREGIR (se rompieron 7 del lote L1)      ATRIBUIR (195 sueltas pasan a ser del lote L1)
 *   ------------------------------------       ---------------------------------------------
 *   movimiento_stock       AJUSTE  -7          movimiento_stock       AJUSTE    0
 *     movimiento_stock_lote  L1     -7           movimiento_stock_lote  L1     +195
 *                                                movimiento_stock_lote  SIN LOTE -195
 * </pre>
 *
 * El movimiento en cero de la atribución no es un truco: la columna
 * {@code movimiento_stock_lote.movimiento_stock_id} es NOT NULL, así que no existe forma de anotar
 * en el ledger sin un movimiento del cual colgar la fila. Y de paso el ajuste queda visible en el
 * historial del lote y en la auditoría, que es donde alguien lo va a buscar.
 *
 * Vive aparte de {@link MovimientoStockService} y de {@link MovimientoStockLoteService} —que ya
 * cargan con las transferencias y con todas las consultas de saldo— para que esta regla se pueda
 * leer y probar entera de un saque.
 */
@Log
@Service
public class AjusteStockLoteService {

    /** Tolerancia al comparar cantidades en punto flotante, igual que en el resto del módulo. */
    private static final double EPSILON = 0.0001;

    @Autowired
    private MovimientoStockService movimientoStockService;

    @Autowired
    private MovimientoStockLoteService movimientoStockLoteService;

    @Autowired
    private LoteService loteService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ModificacionService modificacionService;

    /** Lo que hay que escribir, ya resuelto. Ver {@link #calcular}. */
    @Data
    public static class PlanAjuste {

        private final double cantidadMovimiento;
        private final double cantidadLote;
        private final double cantidadSinTrazar;

        /** Sin diferencia no hay nada que anotar: escribir ceros solo ensuciaría el ledger. */
        public boolean isVacio() {
            return Math.abs(cantidadLote) <= EPSILON;
        }
    }

    /**
     * Decide cuánto va al movimiento agregado y cuánto a cada fila del ledger.
     *
     * Función pura y estática a propósito: es la única parte que puede equivocarse en silencio, y
     * así se prueba sin base ni contexto de Spring.
     *
     * En los dos modos el operador carga la cantidad FINAL del lote, no la diferencia — es la misma
     * cabeza que ya tiene con el ajuste de stock de siempre. La diferencia la saca el sistema.
     *
     * @param saldoLote     saldo actual del lote en esa sucursal, en unidades base.
     * @param cantidadFinal cuántas unidades de ese lote hay realmente.
     */
    public static PlanAjuste calcular(ModoAjusteLote modo, double saldoLote, double cantidadFinal) {
        double diferencia = cantidadFinal - saldoLote;
        if (Math.abs(diferencia) <= EPSILON) {
            return new PlanAjuste(0.0, 0.0, 0.0);
        }
        if (modo == ModoAjusteLote.ATRIBUIR) {
            // El total no se mueve: lo que gana el lote lo pierde el bucket sin trazar. El
            // movimiento agregado va en cero y las dos hijas se compensan, así que
            // SUM(hijas) = padre se sigue cumpliendo.
            return new PlanAjuste(0.0, diferencia, -diferencia);
        }
        return new PlanAjuste(diferencia, diferencia, 0.0);
    }

    /**
     * Las tres cuentas del producto en la sucursal. Es lo que la pantalla necesita para mostrarle
     * al operador el efecto de lo que está por confirmar.
     *
     * El saldo sin trazar se deriva —{@code existencia - lotes reales}— porque no se almacena en
     * ningún lado: es exactamente el hueco que este ajuste viene a cerrar.
     */
    public ResumenStockLoteDto resumen(Long productoId, Long sucursalId) {
        if (productoId == null || sucursalId == null) {
            return new ResumenStockLoteDto(productoId, sucursalId, 0.0, 0.0, 0.0);
        }
        double existencia = movimientoStockService.stockByProductoIdAndSucursalId(productoId, sucursalId);
        double enLotes = 0.0;
        for (StockLoteDto lote : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            // Solo lotes REALES: las filas SIN LOTE son el registro de lo que salió sin trazar, no
            // stock atribuido, y contarlas acá haría que el hueco se tapara solo en el papel.
            if (lote.getLoteId() != null && lote.getCantidadDisponible() != null) {
                enLotes += lote.getCantidadDisponible();
            }
        }
        return new ResumenStockLoteDto(productoId, sucursalId, existencia, enLotes, existencia - enLotes);
    }

    /**
     * Aplica el ajuste. Todo dentro de una transacción: o quedan el movimiento agregado y sus filas
     * hijas, o no queda nada. Un padre sin hijas sería exactamente el problema que esto arregla.
     *
     * @throws IllegalArgumentException si falta un dato o el producto no lleva control de lote. El
     *                                  resolver lo traduce a un error de GraphQL.
     */
    @Transactional
    public AjusteStockLoteResultadoDto ajustar(AjusteStockLoteInput input) {
        validar(input);

        Producto producto = productoService.findById(input.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto " + input.getProductoId()));
        if (!Boolean.TRUE.equals(producto.getLote())) {
            // Sin control de lote no hay nada que desglosar: ese ajuste va por el camino de
            // siempre. Aceptarlo acá escribiría un desglose que nadie consulta.
            throw new IllegalArgumentException("El producto '" + producto.getDescripcion()
                    + "' no tiene control de lote: usá el ajuste de stock normal.");
        }

        Usuario usuario = input.getUsuarioId() != null
                ? usuarioService.findById(input.getUsuarioId()).orElse(null)
                : null;

        Lote lote = resolverLote(input, producto, usuario);
        if (lote == null || lote.getId() == null) {
            throw new IllegalArgumentException("No se pudo resolver el lote del ajuste.");
        }

        double saldoLote = saldoDeLote(input.getProductoId(), input.getSucursalId(), lote.getId());
        PlanAjuste plan = calcular(input.getModo(), saldoLote, input.getCantidadFinal());
        if (plan.isVacio()) {
            throw new IllegalArgumentException(
                    "El lote " + lote.getNumeroLote() + " ya tiene esa cantidad: no hay nada que ajustar.");
        }

        MovimientoStock movimiento = crearMovimientoAgregado(producto, input, plan, usuario);
        List<MovimientoStockLote> filas = new ArrayList<>();
        filas.add(crearFila(movimiento, producto, lote, lote.getNumeroLote(), plan.getCantidadLote(), usuario));
        if (Math.abs(plan.getCantidadSinTrazar()) > EPSILON) {
            filas.add(crearFila(movimiento, producto, null, LoteFefoService.NUMERO_LOTE_SIN_TRAZAR,
                    plan.getCantidadSinTrazar(), usuario));
        }
        for (MovimientoStockLote fila : filas) {
            movimientoStockLoteService.save(fila);
        }

        registrarAuditoria(movimiento, lote, input, plan);

        ResumenStockLoteDto resumen = resumen(input.getProductoId(), input.getSucursalId());
        return new AjusteStockLoteResultadoDto(
                movimiento.getId(),
                movimiento.getSucursalId(),
                lote.getId(),
                lote.getNumeroLote(),
                plan.getCantidadMovimiento(),
                saldoLote + plan.getCantidadLote(),
                resumen);
    }

    private void validar(AjusteStockLoteInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Falta el ajuste.");
        }
        if (input.getProductoId() == null || input.getSucursalId() == null) {
            throw new IllegalArgumentException("El producto y la sucursal son obligatorios.");
        }
        if (input.getModo() == null) {
            throw new IllegalArgumentException("Falta indicar si se corrige la existencia o se atribuye stock.");
        }
        if (input.getCantidadFinal() == null) {
            throw new IllegalArgumentException("Falta la cantidad del lote.");
        }
        // El motivo es opcional, igual que en el ajuste de stock comun: este ajuste tiene que
        // costarle al operador lo mismo que el de un producto sin lote. Si viene, se guarda en la
        // auditoria; si no, el ajuste igual queda registrado con usuario, cantidad y lote.
        if (input.getLoteId() == null
                && (input.getNumeroLote() == null || input.getNumeroLote().trim().isEmpty())) {
            throw new IllegalArgumentException("Hay que elegir un lote o informar su número.");
        }
    }

    /**
     * Con loteId se usa el lote existente; con número se resuelve o se crea.
     *
     * El alta pasa por {@link LoteService#obtenerOCrear}, que es la misma puerta que usa la
     * recepción: así el número se normaliza igual, dos altas del mismo lote terminan en la misma
     * fila y la fecha de retiro se deriva con la misma regla.
     */
    private Lote resolverLote(AjusteStockLoteInput input, Producto producto, Usuario usuario) {
        if (input.getLoteId() != null) {
            return loteService.findById(input.getLoteId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el lote " + input.getLoteId()));
        }
        return loteService.obtenerOCrear(producto, input.getNumeroLote(),
                aFecha(input.getFechaVencimiento(), "vencimiento"),
                aFecha(input.getFechaRetiro(), "retiro"),
                null, usuario);
    }

    private LocalDate aFecha(String valor, String cual) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha de " + cual + " no tiene formato yyyy-MM-dd.");
        }
    }

    /**
     * Saldo del lote en la sucursal. Sale de la misma consulta que alimenta FEFO, así que el número
     * que se ajusta es exactamente el que se ve en el selector y en "Stock por lotes".
     *
     * Un lote sin movimientos en esa sucursal no aparece en el resultado y su saldo es 0, que es
     * justo el caso de la mercadería que se está trazando por primera vez.
     */
    private double saldoDeLote(Long productoId, Long sucursalId, Long loteId) {
        for (StockLoteDto lote : movimientoStockLoteService.stockPorLote(productoId, sucursalId)) {
            if (loteId.equals(lote.getLoteId())) {
                return lote.getCantidadDisponible() != null ? lote.getCantidadDisponible() : 0.0;
            }
        }
        return 0.0;
    }

    private MovimientoStock crearMovimientoAgregado(Producto producto, AjusteStockLoteInput input,
                                                    PlanAjuste plan, Usuario usuario) {
        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setSucursalId(input.getSucursalId());
        movimiento.setProducto(producto);
        movimiento.setTipoMovimiento(TipoMovimiento.AJUSTE);
        // Misma convención que el ajuste de stock de siempre: la referencia de un ajuste es el
        // propio producto, porque no hay documento del cual colgarlo.
        movimiento.setReferencia(producto.getId());
        movimiento.setCantidad(plan.getCantidadMovimiento());
        movimiento.setEstado(true);
        movimiento.setUsuario(usuario);
        movimiento.setCreadoEn(LocalDateTime.now());
        return movimientoStockService.save(movimiento);
    }

    /**
     * Una fila del ledger. Con {@code lote} nulo es el bucket sin trazar: no tiene maestro y por eso
     * el número va desnormalizado, igual que hace la venta cuando FEFO no llega a cubrir todo.
     *
     * La presentación queda nula a propósito: el ajuste se carga en unidades base, que es la unidad
     * en la que vive el ledger. Convertir desde una presentación reintroduciría el redondeo que ya
     * se sacó del camino de la venta.
     */
    private MovimientoStockLote crearFila(MovimientoStock movimiento, Producto producto, Lote lote,
                                          String numeroLote, double cantidad, Usuario usuario) {
        MovimientoStockLote fila = new MovimientoStockLote();
        fila.setSucursalId(movimiento.getSucursalId());
        fila.setMovimientoStockId(movimiento.getId());
        fila.setLote(lote);
        fila.setProducto(producto);
        fila.setNumeroLote(numeroLote);
        fila.setCantidad(cantidad);
        fila.setReferencia(producto.getId());
        fila.setEstado(true);
        fila.setUsuario(usuario);
        fila.setCreadoEn(movimiento.getCreadoEn());
        return fila;
    }

    /**
     * Deja el motivo escrito donde ya se guardan los cambios sensibles del sistema.
     *
     * Va al registro de modificaciones y no a una columna nueva porque
     * {@code operaciones.movimiento_stock} y {@code movimiento_stock_lote} se replican entre central
     * y filiales: agregarles una columna obliga a migrar las dos puntas en orden, y el dato es de
     * auditoría, no de operación.
     *
     * Nunca tumba el ajuste: si la auditoría falla, el stock ya quedó bien y eso es lo que importa.
     */
    private void registrarAuditoria(MovimientoStock movimiento, Lote lote, AjusteStockLoteInput input,
                                    PlanAjuste plan) {
        try {
            String motivo = input.getMotivo() != null && !input.getMotivo().trim().isEmpty()
                    ? input.getMotivo().trim()
                    : "sin motivo";
            String detalle = String.format(
                    "%s | lote %s | movimiento %s | lote %s | sin trazar %s | motivo: %s",
                    input.getModo(), lote.getNumeroLote(),
                    plan.getCantidadMovimiento(), plan.getCantidadLote(), plan.getCantidadSinTrazar(),
                    motivo);
            modificacionService.registrarInsercion(movimiento, "AJUSTE_STOCK_LOTE", "operaciones",
                    "movimiento_stock_lote", detalle);
        } catch (Exception e) {
            log.warning("Error registrando auditoría del ajuste por lote: " + e.getMessage());
        }
    }
}
