package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Lote;
import com.franco.dev.domain.operaciones.dto.LoteDeProductoProjection;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            "ORDER BY CASE WHEN COALESCE(s.saldo, 0) <> 0 THEN 0 ELSE 1 END, " +
            "         CASE WHEN COALESCE(t.saldo, 0) <> 0 THEN 0 ELSE 1 END, " +
            "         CASE WHEN COALESCE(l.fecha_retiro, l.fecha_vencimiento) IS NULL THEN 1 ELSE 0 END, " +
            "         COALESCE(l.fecha_retiro, l.fecha_vencimiento) ASC, l.id ASC",
            countQuery =
            "SELECT COUNT(*) FROM operaciones.lote l " +
            "WHERE l.producto_id = :productoId " +
            "  AND (:texto IS NULL OR l.numero_lote LIKE UPPER(CONCAT('%', :texto, '%')))",
            nativeQuery = true)
    Page<LoteDeProductoProjection> buscarLotesDeProducto(@Param("productoId") Long productoId,
                                                         @Param("sucursalId") Long sucursalId,
                                                         @Param("texto") String texto,
                                                         Pageable pageable);
}
