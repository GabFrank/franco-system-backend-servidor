package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.dto.StockLoteProjection;
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
            "       msl.sucursal_id AS \"sucursalId\", s.nombre AS \"sucursalNombre\", " +
            "       msl.numero_lote AS \"numeroLote\", " +
            "       l.fecha_vencimiento AS \"fechaVencimiento\", l.fecha_retiro AS \"fechaRetiro\", " +
            "       l.estado AS \"estado\", " +
            "       CAST(SUM(msl.cantidad) AS double precision) AS \"cantidadDisponible\" " +
            "FROM operaciones.movimiento_stock_lote msl " +
            "LEFT JOIN operaciones.lote l ON l.id = msl.lote_id " +
            "JOIN productos.producto p ON p.id = msl.producto_id " +
            "LEFT JOIN empresarial.sucursal s ON s.id = msl.sucursal_id " +
            "WHERE msl.estado = true " +
            "  AND (:productoId IS NULL OR msl.producto_id = :productoId) " +
            "  AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "  AND (:estado IS NULL OR l.estado = :estado) " +
            "  AND (:numeroLote IS NULL OR msl.numero_lote LIKE UPPER(CONCAT('%', :numeroLote, '%'))) " +
            "  AND (:texto IS NULL OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "  AND (:vencimientoHasta IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) <= CAST(:vencimientoHasta AS date)) " +
            "GROUP BY l.id, msl.producto_id, p.descripcion, msl.sucursal_id, s.nombre, msl.numero_lote, " +
            "         l.fecha_vencimiento, l.fecha_retiro, l.estado " +
            "HAVING SUM(msl.cantidad) <> 0 " +
            "ORDER BY CASE WHEN COALESCE(l.fecha_retiro, l.fecha_vencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "         COALESCE(l.fecha_retiro, l.fecha_vencimiento) ASC, msl.producto_id, msl.sucursal_id",
            countQuery =
            "SELECT COUNT(*) FROM (" +
            "  SELECT 1 FROM operaciones.movimiento_stock_lote msl " +
            "  LEFT JOIN operaciones.lote l ON l.id = msl.lote_id " +
            "  JOIN productos.producto p ON p.id = msl.producto_id " +
            "  WHERE msl.estado = true " +
            "    AND (:productoId IS NULL OR msl.producto_id = :productoId) " +
            "    AND (:sucursalId IS NULL OR msl.sucursal_id = :sucursalId) " +
            "    AND (:estado IS NULL OR l.estado = :estado) " +
            "    AND (:numeroLote IS NULL OR msl.numero_lote LIKE UPPER(CONCAT('%', :numeroLote, '%'))) " +
            "    AND (:texto IS NULL OR UPPER(p.descripcion) LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "    AND (:vencimientoHasta IS NULL OR COALESCE(l.fecha_retiro, l.fecha_vencimiento) <= CAST(:vencimientoHasta AS date)) " +
            // Sin s.nombre en el GROUP BY: el count no joinea empresarial.sucursal y la columna no
            // resuelve. Agrupar por msl.sucursal_id da exactamente los mismos grupos, porque el
            // nombre depende funcionalmente del id.
            "  GROUP BY l.id, msl.producto_id, p.descripcion, msl.sucursal_id, msl.numero_lote, " +
            "           l.fecha_vencimiento, l.fecha_retiro, l.estado " +
            "  HAVING SUM(msl.cantidad) <> 0) sub",
            nativeQuery = true)
    Page<StockLoteProjection> buscarStockPorLote(@Param("productoId") Long productoId,
                                                 @Param("sucursalId") Long sucursalId,
                                                 @Param("estado") String estado,
                                                 @Param("numeroLote") String numeroLote,
                                                 @Param("texto") String texto,
                                                 @Param("vencimientoHasta") String vencimientoHasta,
                                                 Pageable pageable);
}
