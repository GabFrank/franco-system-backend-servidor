package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.productos.Presentacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.math.BigDecimal;

/**
 * Control de stock negativo: registra las salidas de productos cuyo stock en la sucursal ya era
 * 0 o negativo, y las lista para el equipo de inventario.
 *
 * El stock es siempre el del central (suma de movimientos activos del producto en la sucursal).
 * Las ventas no pasan por aca: las inserta {@link ControlStockNegativoProcesador}.
 *
 * Las escrituras van por JdbcTemplate y no por JPA a proposito. El central usa
 * OpenEntityManagerInViewFilter: todo el request comparte un EntityManager, y un save de JPA que
 * falle hace rollback y clear() de ese EntityManager, dejando detached el item que
 * saveTransferenciaItem acaba de guardar. JdbcTemplate usa su propia conexion.
 */
@Service
public class ControlStockNegativoService {

    private static final Logger log = LoggerFactory.getLogger(ControlStockNegativoService.class);

    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public ControlStockNegativoService(JdbcTemplate jdbc, EntityManager em) {
        this.jdbc = jdbc;
        this.em = em;
    }

    /** El criterio unico: se registra si antes de la salida el stock era 0 o negativo. */
    public static boolean debeRegistrar(BigDecimal stockPrevio) {
        return stockPrevio != null && stockPrevio.signum() <= 0;
    }

    /**
     * Registra el item recien cargado si el stock del origen ya era 0 o negativo.
     *
     * Es un registro de control, no parte de la operacion: nunca lanza. Cualquier falla se loguea
     * y el item se guarda igual.
     *
     * @return true si quedo registrado
     */
    public boolean registrarTransferencia(TransferenciaItem item) {
        try {
            if (item == null || item.getTransferencia() == null
                    || item.getTransferencia().getSucursalOrigen() == null) {
                return false;
            }
            Presentacion presentacion = item.getPresentacionPreTransferencia();
            if (presentacion == null || presentacion.getProducto() == null) {
                return false;
            }
            long sucursalId = item.getTransferencia().getSucursalOrigen().getId();
            long productoId = presentacion.getProducto().getId();

            BigDecimal stockPrevio = stockPrevio(productoId, sucursalId);
            if (!debeRegistrar(stockPrevio)) {
                return false;
            }
            double porPresentacion = presentacion.getCantidad() != null ? presentacion.getCantidad() : 1D;
            double cantidad = item.getCantidadPreTransferencia() != null ? item.getCantidadPreTransferencia() : 0D;
            Long usuarioId = item.getUsuario() != null ? item.getUsuario().getId() : null;

            insertarTransferencia(sucursalId, productoId, cantidad * porPresentacion, stockPrevio,
                    usuarioId, item.getTransferencia().getId(), item.getId());
            return true;
        } catch (Exception ex) {
            log.warn("Control de stock negativo: no se pudo registrar el item {} de transferencia: {}",
                    item != null ? item.getId() : null, ex.getMessage());
            return false;
        }
    }

    /** Stock del producto en la sucursal segun el central, sin pasar por Float. */
    BigDecimal stockPrevio(long productoId, long sucursalId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(cantidad), 0) FROM operaciones.movimiento_stock " +
                "WHERE producto_id = ? AND sucursal_id = ? AND estado",
                BigDecimal.class, productoId, sucursalId);
    }

    void insertarTransferencia(long sucursalId, long productoId, double cantidad, BigDecimal stockPrevio,
                               Long usuarioId, Long transferenciaId, Long itemId) {
        jdbc.update(
                "INSERT INTO operaciones.control_stock_negativo " +
                "  (sucursal_id, producto_id, tipo, cantidad, stock_previo, usuario_id, fecha, referencia_id, item_id) " +
                "VALUES (?, ?, 'TRANSFERENCIA', ?, ?, ?, now(), ?, ?) " +
                "ON CONFLICT (item_id, sucursal_id) WHERE tipo = 'TRANSFERENCIA' DO NOTHING",
                sucursalId, productoId, cantidad, stockPrevio, usuarioId, transferenciaId, itemId);
    }
}
