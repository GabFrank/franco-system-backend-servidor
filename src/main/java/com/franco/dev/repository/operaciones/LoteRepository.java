package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.dto.LoteDeProductoProjection;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoteRepository extends HelperRepository<Lote, Long> {

    /**
     * Busca por la clave natural del lote. El número ya debe venir normalizado (trim + mayúsculas).
     */
    @Query("select e from Lote e where e.producto.id = :productoId and e.numeroLote = :numeroLote")
    Optional<Lote> findByProductoIdAndNumeroLote(@Param("productoId") Long productoId,
                                                 @Param("numeroLote") String numeroLote);

    /**
     * Lotes de un producto, ordenados por FEFO: primero los que hay que retirar antes.
     * Los lotes sin fecha conocida quedan al final.
     */
    @Query("select e from Lote e where e.producto.id = :productoId " +
            "order by case when coalesce(e.fechaRetiro, e.fechaVencimiento) is null then 1 else 0 end, " +
            "coalesce(e.fechaRetiro, e.fechaVencimiento) asc, e.id asc")
    List<Lote> findByProductoId(@Param("productoId") Long productoId);

    /**
     * Buscador paginado de lotes de un producto, con el saldo que tiene cada uno en una sucursal.
     *
     * Parte del MAESTRO y no del ledger a propósito: un lote que todavía no entró a esa sucursal
     * tiene saldo cero y no aparecería en una consulta armada sobre movimiento_stock_lote, pero es
     * exactamente el que hace falta para trazar mercadería que estaba en góndola sin lote asignado.
     *
     * Devuelve DOS saldos: el de la sucursal consultada y el de toda la red. Sin el segundo, un lote
     * que tiene mercadería en otro depósito se ve igual que un lote vacío —los dos en cero— y el
     * operador no puede distinguir "acá no hay" de "no hay en ningún lado".
     *
     * Orden: primero los que tienen saldo acá —el caso normal—, después los que tienen saldo en otra
     * sucursal, y dentro de cada grupo por FEFO, con los que no tienen ninguna fecha al final.
     *
     * Los alias van en camelCase y entre comillas dobles porque Spring Data resuelve la proyección
     * por interfaz buscando el alias exacto del getter y Postgres pasa a minúsculas todo alias sin
     * comillar.
     */
    @Query(value =
            "SELECT l.id AS \"loteId\", l.numero_lote AS \"numeroLote\", " +
            "       l.fecha_vencimiento AS \"fechaVencimiento\", l.fecha_retiro AS \"fechaRetiro\", " +
            "       l.estado AS \"estado\", " +
            "       EXISTS (SELECT 1 FROM operaciones.lote_archivado a2 WHERE a2.lote_id = l.id) AS \"archivado\", " +
            "       CAST(COALESCE(s.saldo, 0) AS double precision) AS \"saldo\", " +
            "       CAST(COALESCE(t.saldo, 0) AS double precision) AS \"saldoTotal\" " +
            "FROM operaciones.lote l " +
            "LEFT JOIN ( " +
            "  SELECT lote_id, SUM(cantidad) AS saldo " +
            "  FROM operaciones.movimiento_stock_lote " +
            "  WHERE estado = true AND sucursal_id = :sucursalId " +
            "  GROUP BY lote_id " +
            ") s ON s.lote_id = l.id " +
            "LEFT JOIN ( " +
            "  SELECT lote_id, SUM(cantidad) AS saldo " +
            "  FROM operaciones.movimiento_stock_lote " +
            "  WHERE estado = true " +
            "  GROUP BY lote_id " +
            ") t ON t.lote_id = l.id " +
            "WHERE l.producto_id = :productoId " +
            "  AND (:texto IS NULL OR l.numero_lote LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "  AND (:incluirArchivados = true OR COALESCE(t.saldo, 0) <> 0 " +
            "       OR NOT EXISTS (SELECT 1 FROM operaciones.lote_archivado a WHERE a.lote_id = l.id)) " +
            "  AND (:incluirArchivados = true " +
            "       OR NOT EXISTS (SELECT 1 FROM operaciones.lote_eliminado e WHERE e.lote_id = l.id)) " +
            "ORDER BY CASE WHEN COALESCE(s.saldo, 0) <> 0 THEN 0 ELSE 1 END, " +
            "         CASE WHEN COALESCE(t.saldo, 0) <> 0 THEN 0 ELSE 1 END, " +
            "         CASE WHEN COALESCE(l.fecha_retiro, l.fecha_vencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "         COALESCE(l.fecha_retiro, l.fecha_vencimiento) ASC, l.id ASC",
            countQuery =
            "SELECT COUNT(*) FROM operaciones.lote l " +
            "WHERE l.producto_id = :productoId " +
            "  AND (:texto IS NULL OR l.numero_lote LIKE UPPER(CONCAT('%', :texto, '%'))) " +
            "  AND (:incluirArchivados = true " +
            "       OR COALESCE((SELECT SUM(m.cantidad) FROM operaciones.movimiento_stock_lote m WHERE m.estado = true AND m.lote_id = l.id), 0) <> 0 " +
            "       OR NOT EXISTS (SELECT 1 FROM operaciones.lote_archivado a WHERE a.lote_id = l.id)) " +
            "  AND (:incluirArchivados = true " +
            "       OR NOT EXISTS (SELECT 1 FROM operaciones.lote_eliminado e WHERE e.lote_id = l.id))",
            nativeQuery = true)
    Page<LoteDeProductoProjection> buscarLotesDeProducto(@Param("productoId") Long productoId,
                                                         @Param("sucursalId") Long sucursalId,
                                                         @Param("texto") String texto,
                                                         @Param("incluirArchivados") boolean incluirArchivados,
                                                         Pageable pageable);

    /** Saldo del lote sumado en TODAS las sucursales. Archivar exige que sea 0. */
    @Query(value = "SELECT CAST(COALESCE(SUM(cantidad), 0) AS double precision) " +
            "FROM operaciones.movimiento_stock_lote WHERE estado = true AND lote_id = :loteId",
            nativeQuery = true)
    Double saldoTotalLote(@Param("loteId") Long loteId);

    @Query(value = "SELECT COUNT(*) > 0 FROM operaciones.lote_archivado WHERE lote_id = :loteId",
            nativeQuery = true)
    boolean estaArchivado(@Param("loteId") Long loteId);

    @Modifying
    @Query(value = "INSERT INTO operaciones.lote_archivado (lote_id, usuario_id, motivo, archivado_en) " +
            "VALUES (:loteId, :usuarioId, :motivo, now()) ON CONFLICT (lote_id) DO NOTHING",
            nativeQuery = true)
    int archivar(@Param("loteId") Long loteId, @Param("usuarioId") Long usuarioId,
                 @Param("motivo") String motivo);

    @Modifying
    @Query(value = "DELETE FROM operaciones.lote_archivado WHERE lote_id = :loteId", nativeQuery = true)
    int desarchivar(@Param("loteId") Long loteId);

    // --- Lotes eliminados (baja logica, V255.8) ---

    @Query(value = "SELECT COUNT(*) > 0 FROM operaciones.lote_eliminado WHERE lote_id = :loteId",
            nativeQuery = true)
    boolean estaEliminado(@Param("loteId") Long loteId);

    /**
     * Cuantas filas del central usan este lote (ledger, transferencias, devoluciones, inventario,
     * pendientes resueltos). Eliminar exige 0: un lote con historia se archiva o se bloquea.
     */
    @Query(value = "SELECT (SELECT COUNT(*) FROM operaciones.movimiento_stock_lote WHERE lote_id = :loteId)" +
            " + (SELECT COUNT(*) FROM operaciones.transferencia_item_lote WHERE lote_id = :loteId)" +
            " + (SELECT COUNT(*) FROM operaciones.devolucion_item WHERE lote_id = :loteId)" +
            " + (SELECT COUNT(*) FROM operaciones.inventario_producto_item WHERE lote_id = :loteId)" +
            " + (SELECT COUNT(*) FROM operaciones.lote_pendiente_resuelto WHERE lote_id = :loteId)",
            nativeQuery = true)
    Long contarReferencias(@Param("loteId") Long loteId);

    @Modifying
    @Query(value = "INSERT INTO operaciones.lote_eliminado (lote_id, copia, estado_anterior, motivo, usuario_id, eliminado_en) " +
            "SELECT l.id, to_jsonb(l), l.estado, :motivo, :usuarioId, now() FROM operaciones.lote l WHERE l.id = :loteId " +
            "ON CONFLICT (lote_id) DO NOTHING",
            nativeQuery = true)
    int registrarEliminado(@Param("loteId") Long loteId, @Param("usuarioId") Long usuarioId,
                           @Param("motivo") String motivo);

    @Query(value = "SELECT estado_anterior FROM operaciones.lote_eliminado WHERE lote_id = :loteId",
            nativeQuery = true)
    String estadoAnteriorEliminado(@Param("loteId") Long loteId);

    @Modifying
    @Query(value = "DELETE FROM operaciones.lote_eliminado WHERE lote_id = :loteId", nativeQuery = true)
    int borrarEliminado(@Param("loteId") Long loteId);
}
