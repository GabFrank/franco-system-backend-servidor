package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.ClienteLoteProjection;
import com.franco.dev.domain.operaciones.dto.MovimientoLoteProjection;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteProjection;
import com.franco.dev.domain.operaciones.dto.StockLoteSucursalProjection;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoStockLoteRepository
        extends HelperRepository<MovimientoStockLote, EmbebedPrimaryKey> {

    default Class<MovimientoStockLote> getEntityClass() {
        return MovimientoStockLote.class;
    }

    @Query("SELECT MAX(e.id) FROM MovimientoStockLote e WHERE e.sucursalId = :sucursalId")
    Long findMaxId(@Param("sucursalId") Long sucursalId);

    List<MovimientoStockLote> findByMovimientoStockIdAndSucursalId(Long movimientoStockId, Long sucursalId);

    /**
     * Borra el desglose de un movimiento para volver a generarlo. Se usa cuando la cantidad del
     * movimiento cambia entre etapas de una transferencia y el reparto entre lotes deja de valer.
     */
    void deleteByMovimientoStockIdAndSucursalId(Long movimientoStockId, Long sucursalId);

    /**
     * Saldo disponible por lote de un producto en una sucursal. Equivale a la vista
     * operaciones.v_stock_lote filtrada, ordenada por FEFO (First Expired, First Out).
     *
     * FEFO ordena por la fecha de RETIRO, no por el vencimiento: la idea es sacar la mercadería
     * antes de que efectivamente venza. Si el producto no tiene días configurados no hay fecha de
     * retiro y se cae al vencimiento. Los lotes sin ninguna fecha conocida quedan al final.
     *
     * Devuelve todos los estados: el filtro de LIBERADO lo aplica quien consume, para que las
     * pantallas de consulta puedan ver también lo bloqueado.
     */
    @Query("SELECT new com.franco.dev.domain.operaciones.dto.StockLoteDto(" +
            "  l.id, e.producto.id, e.sucursalId, e.numeroLote, " +
            "  l.fechaVencimiento, l.fechaRetiro, l.estado, SUM(e.cantidad)) " +
            "FROM MovimientoStockLote e LEFT JOIN e.lote l " +
            "WHERE e.estado = true AND e.producto.id = :productoId AND e.sucursalId = :sucursalId " +
            "GROUP BY l.id, e.producto.id, e.sucursalId, e.numeroLote, " +
            "  l.fechaVencimiento, l.fechaRetiro, l.estado " +
            "HAVING SUM(e.cantidad) <> 0 " +
            "ORDER BY CASE WHEN COALESCE(l.fechaRetiro, l.fechaVencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "  COALESCE(l.fechaRetiro, l.fechaVencimiento) ASC, l.id ASC")
    List<StockLoteDto> stockPorLote(@Param("productoId") Long productoId,
                                    @Param("sucursalId") Long sucursalId);

    /**
     * Consulta general de stock por lote con filtros opcionales, para la pantalla
     * "Stock por lotes". Responde la pregunta "¿dónde tengo qué?".
     *
     * Todos los filtros son opcionales: pasar null los desactiva. El orden es FEFO
     * (fecha de retiro más próxima primero), que es el mismo con el que se va a consumir.
     *
     * Se consulta contra el ledger agrupado en vez de la vista v_stock_lote para poder paginar
     * y filtrar sin depender del plan de la vista.
     *
     * Agrupa por LOTE, no por lote y sucursal: la fila es el lote y la cantidad es su saldo total.
     * El desglose por sucursal se pide aparte con {@link #stockLotePorSucursal(Long)}. Con
     * :sucursalId el total queda restringido a esa sucursal, que es lo que espera la pantalla
     * cuando se filtra. Por eso el DTO sale con sucursalId y sucursalNombre en null.
     *
     * Los alias van en camelCase y ENTRE COMILLAS DOBLES a propósito: Spring Data resuelve una
     * proyección por interfaz sobre una query nativa buscando el alias exacto del getter
     * ({@code getLoteId()} -> {@code "loteId"}), sin convertir snake_case. Un alias
     * {@code lote_id} devuelve null en vez de fallar, y la pantalla queda con las columnas
     * vacías. Las comillas son necesarias porque Postgres pasa a minúsculas todo alias sin
     * comillar ({@code AS loteId} llegaría como {@code loteid} y tampoco matchearía).
     */
    @Query(value =
            "SELECT l.id AS \"loteId\", msl.producto_id AS \"productoId\", " +
            "       p.descripcion AS \"productoDescripcion\", " +
            "       msl.numero_lote AS \"numeroLote\", " +
            "       l.fecha_vencimiento AS \"fechaVencimiento\", l.fecha_retiro AS \"fechaRetiro\", " +
            "       l.estado AS \"estado\", pp.nombre AS \"proveedorNombre\", " +
            "       CAST(SUM(msl.cantidad) AS double precision) AS \"cantidadDisponible\" " +
            "FROM operaciones.movimiento_stock_lote msl " +
            "LEFT JOIN operaciones.lote l ON l.id = msl.lote_id " +
            "LEFT JOIN personas.proveedor pr ON pr.id = l.proveedor_id " +
            "LEFT JOIN personas.persona pp ON pp.id = pr.persona_id " +
            "JOIN productos.producto p ON p.id = msl.producto_id " +
            "WHERE msl.estado = true " +
            "  AND (:productoId IS NULL OR msl.producto_id = :productoId) " +
            "  AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND (:proveedorId IS NULL OR l.proveedor_id = :proveedorId) " +
            "  AND (:estado IS NULL OR l.estado = :estado) " +
            "  AND (:numeroLote IS NULL OR msl.numero_lote LIKE UPPER(CONCAT('%', :numeroLote, '%'))) " +
            "  AND (:texto IS NULL OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "  AND (:vencimientoDesde IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) >= CAST(:vencimientoDesde AS date)) " +
            "  AND (:vencimientoHasta IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) <= CAST(:vencimientoHasta AS date)) " +
            "GROUP BY l.id, msl.producto_id, p.descripcion, msl.numero_lote, " +
            "         l.fecha_vencimiento, l.fecha_retiro, l.estado, pp.nombre " +
            "HAVING SUM(msl.cantidad) <> 0 " +
            "ORDER BY CASE WHEN COALESCE(l.fecha_retiro, l.fecha_vencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "         COALESCE(l.fecha_retiro, l.fecha_vencimiento) ASC, msl.producto_id, msl.numero_lote",
            countQuery =
            "SELECT COUNT(*) FROM (" +
            "  SELECT 1 FROM operaciones.movimiento_stock_lote msl " +
            "  LEFT JOIN operaciones.lote l ON l.id = msl.lote_id " +
            "  JOIN productos.producto p ON p.id = msl.producto_id " +
            "  WHERE msl.estado = true " +
            "    AND (:productoId IS NULL OR msl.producto_id = :productoId) " +
            "    AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "    AND (:proveedorId IS NULL OR l.proveedor_id = :proveedorId) " +
            "    AND (:estado IS NULL OR l.estado = :estado) " +
            "    AND (:numeroLote IS NULL OR msl.numero_lote LIKE UPPER(CONCAT('%', :numeroLote, '%'))) " +
            "    AND (:texto IS NULL OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "    AND (:vencimientoDesde IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) >= CAST(:vencimientoDesde AS date)) " +
            "    AND (:vencimientoHasta IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) <= CAST(:vencimientoHasta AS date)) " +
            "  GROUP BY l.id, msl.producto_id, p.descripcion, msl.numero_lote, " +
            "           l.fecha_vencimiento, l.fecha_retiro, l.estado " +
            "  HAVING SUM(msl.cantidad) <> 0) sub",
            nativeQuery = true)
    Page<StockLoteProjection> buscarStockPorLote(@Param("productoId") Long productoId,
                                                 @Param("sucursalId") Long sucursalId,
                                                 @Param("proveedorId") Long proveedorId,
                                                 @Param("estado") String estado,
                                                 @Param("numeroLote") String numeroLote,
                                                 @Param("texto") String texto,
                                                 @Param("vencimientoDesde") String vencimientoDesde,
                                                 @Param("vencimientoHasta") String vencimientoHasta,
                                                 Pageable pageable);

    /**
     * Desglose por sucursal del saldo de un lote, para expandir una fila de "Stock por lotes".
     *
     * Parte del maestro de sucursales y no del ledger, así que devuelve todas las sucursales
     * ACTIVAS, con 0 en las que no tienen movimientos de ese lote. Poner esos ceros acá y no en la
     * pantalla mantiene el cálculo del lado del servidor y evita que el frontend tenga que cruzar
     * el resultado contra otro listado.
     *
     * Las condiciones del lote van en el ON y no en el WHERE a propósito: en el WHERE
     * descartarían las filas sin match y el LEFT JOIN dejaría de tener efecto. El filtro de
     * activo sí va en el WHERE: aplica a la sucursal, no al match.
     *
     * COALESCE sobre activo porque la columna es nullable y su default es true: una sucursal sin
     * el dato cargado se trata como activa, no como dada de baja.
     */
    @Query(value =
            "SELECT s.id AS \"sucursalId\", s.nombre AS \"sucursalNombre\", " +
            "       CAST(COALESCE(SUM(msl.cantidad), 0) AS double precision) AS \"cantidadDisponible\" " +
            "FROM empresarial.sucursal s " +
            "LEFT JOIN operaciones.movimiento_stock_lote msl " +
            "       ON msl.sucursal_id = s.id AND msl.lote_id = :loteId AND msl.estado = true " +
            "WHERE COALESCE(s.activo, true) = true " +
            "GROUP BY s.id, s.nombre " +
            "ORDER BY s.id",
            nativeQuery = true)
    List<StockLoteSucursalProjection> stockLotePorSucursal(@Param("loteId") Long loteId);

    /**
     * Historial de un lote: todos los movimientos que lo tocaron, del más reciente al más viejo.
     *
     * Es la lectura inversa de {@link #findByMovimientoStockIdAndSucursalId(Long, Long)} y es lo
     * que hace utilizable el bloqueo por recall: sin esto se puede sacar el lote de circulación
     * pero no se puede saber a quién ya se le vendió.
     *
     * El JOIN con movimiento_stock va por (id, sucursal_id) porque esa es su clave primaria; con
     * solo el id la fila de otra sucursal con el mismo número entraría en el resultado. Va como
     * LEFT JOIN para que un movimiento padre faltante deje el tipo en null en vez de perder la
     * fila del ledger, que es la que lleva la cantidad.
     *
     * Se ordena por fecha DESCENDENTE y no por id: el id es secuencial por sucursal, así que
     * ordenar por él mezclaría el orden real entre origen y destino de una transferencia.
     *
     * No se filtra por lote_id IS NOT NULL porque el parámetro ya lo hace: las filas del bucket
     * "SIN LOTE" tienen lote_id nulo y nunca matchean.
     *
     * OJO con ms.referencia: NO es el documento, es el ÍTEM del documento, porque el ledger se
     * escribe por ítem. Mostrarla tal cual como "documento" manda al operador a buscar un id que
     * no existe en la tabla que va a abrir. Por eso se resuelve al padre en "documentoId", con un
     * LEFT JOIN por tipo:
     *
     *   VENTA         -> venta_item.venta_id
     *   COMPRA        -> recepcion_mercaderia_item.recepcion_mercaderia_id
     *   TRANSFERENCIA -> transferencia_item.transferencia_id
     *
     * Solo venta_item lleva sucursal_id y por eso es el único que matchea por (id, sucursal_id);
     * los otros dos son de id global. Sumarles sucursal_id al ON no devolvería nada.
     *
     * La condición de tipo va DENTRO del ON de cada join: en el WHERE descartaría las filas de los
     * otros tipos en vez de dejarles el documento en null.
     */
    @Query(value =
            "SELECT msl.id AS \"id\", msl.sucursal_id AS \"sucursalId\", " +
            "       msl.creado_en AS \"fecha\", s.nombre AS \"sucursalNombre\", " +
            // CAST y no ::text: Hibernate parsea los dos puntos de una query nativa como inicio de
            // parametro con nombre, se come ":text" y le manda a Postgres "tipo_movimiento:" suelto.
            "       CAST(ms.tipo_movimiento AS text) AS \"tipoMovimiento\", " +
            "       ms.referencia AS \"referencia\", " +
            "       COALESCE(vi.venta_id, rmi.recepcion_mercaderia_id, ti.transferencia_id) " +
            "           AS \"documentoId\", " +
            "       CAST(msl.cantidad AS double precision) AS \"cantidad\", " +
            "       COALESCE(u.nickname, p.nombre) AS \"usuarioNombre\" " +
            "FROM operaciones.movimiento_stock_lote msl " +
            "LEFT JOIN operaciones.movimiento_stock ms " +
            "       ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "LEFT JOIN operaciones.venta_item vi " +
            "       ON CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "      AND vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
            "LEFT JOIN operaciones.recepcion_mercaderia_item rmi " +
            "       ON CAST(ms.tipo_movimiento AS text) = 'COMPRA' AND rmi.id = ms.referencia " +
            "LEFT JOIN operaciones.transferencia_item ti " +
            "       ON CAST(ms.tipo_movimiento AS text) = 'TRANSFERENCIA' AND ti.id = ms.referencia " +
            "LEFT JOIN empresarial.sucursal s ON s.id = msl.sucursal_id " +
            "LEFT JOIN personas.usuario u ON u.id = msl.usuario_id " +
            "LEFT JOIN personas.persona p ON p.id = u.persona_id " +
            "WHERE msl.estado = true AND msl.lote_id = :loteId " +
            "  AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND (:tipoMovimiento IS NULL " +
            "       OR CAST(ms.tipo_movimiento AS text) = :tipoMovimiento) " +
            "ORDER BY msl.creado_en DESC, msl.id DESC",
            countQuery =
            "SELECT COUNT(*) FROM operaciones.movimiento_stock_lote msl " +
            "LEFT JOIN operaciones.movimiento_stock ms " +
            "       ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "WHERE msl.estado = true AND msl.lote_id = :loteId " +
            "  AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND (:tipoMovimiento IS NULL " +
            "       OR CAST(ms.tipo_movimiento AS text) = :tipoMovimiento)",
            nativeQuery = true)
    Page<MovimientoLoteProjection> movimientosPorLote(@Param("loteId") Long loteId,
                                                      @Param("sucursalId") Long sucursalId,
                                                      @Param("tipoMovimiento") String tipoMovimiento,
                                                      Pageable pageable);

    /**
     * A qué clientes se le vendió un lote, una fila por VENTA, de la más reciente a la más vieja.
     *
     * Es la lista que hace accionable el recall: cambiar el estado del lote lo saca del mostrador,
     * pero avisar exige saber a quién llamar.
     *
     * No se agrupa por cliente aunque eso repita al que compró varias veces: cada fila tiene que
     * poder abrir su venta, y un cliente agrupado no tiene un único número al que apuntar.
     *
     * Se agrupa por venta y no se devuelve el ítem crudo porque una misma venta puede llevar dos
     * líneas del mismo lote; sin el GROUP BY la venta aparecería duplicada.
     *
     * :rastreable parte el resultado en dos conjuntos que no se solapan. En true devuelve las
     * ventas con cliente identificado, que es lo que sirve para llamar; en false las de mostrador,
     * que no identifican a nadie pero igual se pueden abrir para ver qué se vendió. Nunca los dos
     * juntos: mezclados, las miles de filas de mostrador tapan a los pocos clientes reales.
     *
     * El cliente se descarta por nombre y no solo por id porque 'SIN NOMBRE' es la marca que ya
     * usa el resto del sistema para la venta anónima (ver FacturaLegalSpecification); el id 0 se
     * chequea igual por si algún registro quedó sin persona asociada.
     *
     * El CAST del parámetro es necesario: sin él Postgres no puede inferir el tipo del bind y
     * falla con "could not determine data type of parameter".
     *
     * La cantidad se invierte porque el ledger guarda las salidas en negativo, y lo que la
     * pantalla necesita mostrar es cuánto se llevó cada uno.
     */
    @Query(value =
            "SELECT v.id AS \"ventaId\", v.sucursal_id AS \"sucursalId\", " +
            "       s.nombre AS \"sucursalNombre\", v.creado_en AS \"fecha\", " +
            "       v.cliente_id AS \"clienteId\", pc.nombre AS \"clienteNombre\", " +
            "       pc.documento AS \"clienteDocumento\", " +
            "       pc.direccion AS \"clienteDireccion\", " +
            "       CAST(-SUM(msl.cantidad) AS double precision) AS \"cantidad\" " +
            "FROM operaciones.movimiento_stock_lote msl " +
            "JOIN operaciones.movimiento_stock ms " +
            "     ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "JOIN operaciones.venta_item vi " +
            "     ON vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
            "JOIN operaciones.venta v ON v.id = vi.venta_id AND v.sucursal_id = vi.sucursal_id " +
            "LEFT JOIN empresarial.sucursal s ON s.id = v.sucursal_id " +
            "LEFT JOIN personas.cliente c ON c.id = v.cliente_id " +
            "LEFT JOIN personas.persona pc ON pc.id = c.persona_id " +
            "WHERE msl.estado = true AND msl.lote_id = :loteId " +
            "  AND CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "  AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND ((CAST(:rastreable AS boolean) = true " +
            "        AND v.cliente_id <> 0 " +
            "        AND UPPER(COALESCE(pc.nombre, '')) <> 'SIN NOMBRE') " +
            "       OR (CAST(:rastreable AS boolean) = false " +
            "        AND (v.cliente_id = 0 " +
            "             OR UPPER(COALESCE(pc.nombre, '')) = 'SIN NOMBRE'))) " +
            "GROUP BY v.id, v.sucursal_id, s.nombre, v.creado_en, v.cliente_id, " +
            "         pc.nombre, pc.documento, pc.direccion " +
            "ORDER BY v.creado_en DESC, v.id DESC",
            countQuery =
            "SELECT COUNT(*) FROM (" +
            "  SELECT 1 FROM operaciones.movimiento_stock_lote msl " +
            "  JOIN operaciones.movimiento_stock ms " +
            "       ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "  JOIN operaciones.venta_item vi " +
            "       ON vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
            "  JOIN operaciones.venta v ON v.id = vi.venta_id AND v.sucursal_id = vi.sucursal_id " +
            "  LEFT JOIN personas.cliente c ON c.id = v.cliente_id " +
            "  LEFT JOIN personas.persona pc ON pc.id = c.persona_id " +
            "  WHERE msl.estado = true AND msl.lote_id = :loteId " +
            "    AND CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "    AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "    AND ((CAST(:rastreable AS boolean) = true " +
            "        AND v.cliente_id <> 0 " +
            "        AND UPPER(COALESCE(pc.nombre, '')) <> 'SIN NOMBRE') " +
            "       OR (CAST(:rastreable AS boolean) = false " +
            "        AND (v.cliente_id = 0 " +
            "             OR UPPER(COALESCE(pc.nombre, '')) = 'SIN NOMBRE'))) " +
            "  GROUP BY v.id, v.sucursal_id) sub",
            nativeQuery = true)
    Page<ClienteLoteProjection> clientesPorLote(@Param("loteId") Long loteId,
                                                @Param("sucursalId") Long sucursalId,
                                                @Param("rastreable") Boolean rastreable,
                                                Pageable pageable);
}
