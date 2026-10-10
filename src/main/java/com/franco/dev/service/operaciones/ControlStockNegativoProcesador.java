package com.franco.dev.service.operaciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Un ciclo del control de stock negativo para una sucursal, en su propia transaccion.
 *
 * Bean separado de {@link ControlStockNegativoScheduler} a proposito: invocado por
 * {@code this.} el proxy de Spring no aplicaria {@code @Transactional} (mismo motivo que
 * {@code RetiroTesoreriaProcesador}).
 *
 * Las ventas nacen en la filial y llegan por replicacion logica, sin pasar por codigo de la
 * aplicacion: por eso se sondea. Todos los movimientos VENTA de una sucursal salen de la secuencia
 * de su filial (ids pares, V223.1), asi que un cursor por id sobre ese tipo recorre una sola serie.
 *
 * El stock previo de una venta es la suma de los movimientos activos del producto en la sucursal
 * con fecha ANTERIOR a la de la venta. No es "el stock al momento de procesar": una venta posterior
 * que ya llego no debe contar.
 *
 * Se registra por MOVIMIENTO, no por item de venta: un mismo item puede tener mas de un movimiento
 * activo (descuentos duplicados), y esos son justamente los que inventario necesita ver.
 */
@Service
public class ControlStockNegativoProcesador {

    /** Ventas nuevas que se evaluan por ciclo y por sucursal. Acota el costo cuando una filial vuelve de estar offline. */
    static final int LOTE = 500;
    /**
     * Cuantos ids hacia atras se miran buscando ventas que se confirmaron fuera de orden. Es solo
     * la cota del escaneo por el indice (sucursal_id, id): dentro de ese tramo se re-evaluan
     * unicamente las ventas de los ultimos 15 minutos, que es el filtro que importa.
     */
    static final long SOLAPE_IDS = 5000;

    @PersistenceContext
    private EntityManager em;

    static long rangoDesde(long ultimo, long inicial) {
        return Math.max(ultimo - SOLAPE_IDS, inicial);
    }

    /**
     * Sucursales que toca sondear, la que hace mas tiempo no se procesa primero (asi un ciclo que
     * se corta por tiempo no deja siempre afuera a las mismas). Una sucursal que nunca tuvo una
     * venta se sondea una vez por hora: su sondeo recorre todo su historial de movimientos.
     */
    @Transactional(readOnly = true)
    public List<Long> sucursales() {
        List<?> filas = em.createNativeQuery(
                "SELECT s.id FROM empresarial.sucursal s " +
                "LEFT JOIN operaciones.control_stock_negativo_cursor c ON c.sucursal_id = s.id " +
                "WHERE c.sucursal_id IS NULL OR c.ultimo_movimiento_id > 0 " +
                "   OR c.actualizado_en < now() - interval '1 hour' " +
                "ORDER BY c.actualizado_en ASC NULLS FIRST, s.id").getResultList();
        List<Long> ids = new ArrayList<>();
        for (Object f : filas) ids.add(((Number) f).longValue());
        return ids;
    }

    /** @return cuantas ventas se registraron en el control */
    @Transactional
    public int procesarSucursal(Long sucursalId) {
        List<?> cursor = em.createNativeQuery(
                "SELECT ultimo_movimiento_id, inicial_movimiento_id " +
                "FROM operaciones.control_stock_negativo_cursor WHERE sucursal_id = :s FOR UPDATE")
                .setParameter("s", sucursalId).getResultList();

        if (cursor.isEmpty()) {
            // Primera vez: se arranca desde la ultima venta que ya existe. Sin carga retroactiva.
            em.createNativeQuery(
                    "INSERT INTO operaciones.control_stock_negativo_cursor " +
                    "  (sucursal_id, ultimo_movimiento_id, inicial_movimiento_id, ultimo_creado_en) " +
                    "SELECT :s, COALESCE(x.id, 0), COALESCE(x.id, 0), x.creado_en " +
                    "FROM (SELECT 1) uno LEFT JOIN LATERAL (" +
                    "    SELECT id, creado_en FROM operaciones.movimiento_stock " +
                    "    WHERE sucursal_id = :s AND tipo_movimiento = 'VENTA' ORDER BY id DESC LIMIT 1) x ON true " +
                    "ON CONFLICT (sucursal_id) DO NOTHING")
                    .setParameter("s", sucursalId).executeUpdate();
            return 0;
        }

        Object[] fila = (Object[]) cursor.get(0);
        long ultimo = ((Number) fila[0]).longValue();
        long inicial = ((Number) fila[1]).longValue();

        Object hasta = em.createNativeQuery(
                "SELECT max(t.id) FROM (SELECT id FROM operaciones.movimiento_stock " +
                "  WHERE sucursal_id = :s AND id > :u AND tipo_movimiento = 'VENTA' " +
                "  ORDER BY id LIMIT :l) t")
                .setParameter("s", sucursalId).setParameter("u", ultimo).setParameter("l", LOTE)
                .getSingleResult();
        if (hasta == null) {
            // Sin ventas nuevas. Se anota la pasada: ordena el proximo ciclo y espacia el sondeo
            // de las sucursales que nunca vendieron. La venta que llego fuera de orden se levanta
            // en el proximo ciclo con ventas nuevas: el solapamiento es relativo a ultimo_creado_en.
            em.createNativeQuery(
                    "UPDATE operaciones.control_stock_negativo_cursor SET actualizado_en = now() " +
                    "WHERE sucursal_id = :s").setParameter("s", sucursalId).executeUpdate();
            return 0;
        }
        long hastaId = ((Number) hasta).longValue();

        int registradas = em.createNativeQuery(
                "INSERT INTO operaciones.control_stock_negativo " +
                "  (sucursal_id, producto_id, tipo, cantidad, stock_previo, usuario_id, fecha, " +
                "   referencia_id, item_id, movimiento_stock_id) " +
                "SELECT ms.sucursal_id, ms.producto_id, 'VENTA', -ms.cantidad, sp.stock, ms.usuario_id, " +
                "       ms.creado_en, vi.venta_id, ms.referencia, ms.id " +
                "FROM operaciones.movimiento_stock ms " +
                "CROSS JOIN LATERAL (SELECT COALESCE(SUM(p.cantidad), 0) AS stock " +
                "    FROM operaciones.movimiento_stock p " +
                "    WHERE p.producto_id = ms.producto_id AND p.sucursal_id = ms.sucursal_id " +
                "      AND p.estado AND p.creado_en < ms.creado_en) sp " +
                "LEFT JOIN operaciones.venta_item vi " +
                "    ON vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
                "WHERE ms.sucursal_id = :s AND ms.id > :desde AND ms.id <= :hasta " +
                "  AND ms.tipo_movimiento = 'VENTA' AND ms.estado AND ms.creado_en IS NOT NULL " +
                // Lo nuevo, mas lo ya recorrido de los ultimos 15 minutos (confirmado fuera de orden).
                "  AND (ms.id > :ultimo OR ms.creado_en >= (" +
                "        SELECT c.ultimo_creado_en - interval '15 minutes' " +
                "        FROM operaciones.control_stock_negativo_cursor c WHERE c.sucursal_id = :s)) " +
                "  AND sp.stock <= 0 " +
                "ON CONFLICT (sucursal_id, movimiento_stock_id) WHERE movimiento_stock_id IS NOT NULL DO NOTHING")
                .setParameter("s", sucursalId)
                .setParameter("desde", rangoDesde(ultimo, inicial))
                .setParameter("hasta", hastaId)
                .setParameter("ultimo", ultimo)
                .executeUpdate();

        em.createNativeQuery(
                "UPDATE operaciones.control_stock_negativo_cursor c " +
                "SET ultimo_movimiento_id = :h, actualizado_en = now(), " +
                "    ultimo_creado_en = COALESCE((SELECT ms.creado_en FROM operaciones.movimiento_stock ms " +
                "        WHERE ms.sucursal_id = :s AND ms.id = :h), c.ultimo_creado_en) " +
                "WHERE c.sucursal_id = :s")
                .setParameter("h", hastaId).setParameter("s", sucursalId).executeUpdate();

        return registradas;
    }
}
