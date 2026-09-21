package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface CobroDetalleRepository extends HelperRepository<CobroDetalle, EmbebedPrimaryKey> {
        default Class<CobroDetalle> getEntityClass() {
                return CobroDetalle.class;
        }

        public List<CobroDetalle> findByCobroIdAndSucursalId(Long id, Long sucId);

        @Query(value = "select * from operaciones.cobro_detalle cd " +
                        "left join operaciones.cobro c on cd.cobro_id = c.id " +
                        "left join operaciones.venta v on v.cobro_id = c.id " +
                        "left join financiero.pdv_caja pc on pc.id = v.caja_id " +
                        "where v.estado = 'CONCLUIDA' and pc.id = ?1 and cd.sucursal_id = ?2", nativeQuery = true)
        public List<CobroDetalle> findByCajaId(Long id, Long sucId);

        // Monto por fila: el vuelto se guarda negativo (y unas filas viejas en positivo), por eso
        // se resta con ABS. El total va en guaranies (con el cambio, NULL = guarani); el desglose
        // por moneda se muestra con su simbolo, asi que queda en la moneda original. El FILTER
        // evita que el LEFT JOIN de una forma de pago sin cobros cuente (NULL, NULL) como 1.

        // Query sin filtros (datos históricos completos)
        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "fp.descripcion, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "LEFT JOIN ( " +
                        "    SELECT cd2.forma_pago_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1) WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1)) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, fp.descripcion " +
                        "ORDER BY total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerEstadisticasFormaPago();

        // Query por sucursal sin filtros de fecha
        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "fp.descripcion, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "LEFT JOIN ( " +
                        "    SELECT cd2.forma_pago_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1) WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1)) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id AND cd.sucursal_id = ?1 " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, fp.descripcion " +
                        "ORDER BY total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerEstadisticasFormaPagoPorSucursal(Long sucursalId);

        // Query con filtros de fecha (todas las sucursales)
        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "fp.descripcion, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "LEFT JOIN ( " +
                        "    SELECT cd2.forma_pago_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1) WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1)) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "    AND cd.creado_en >= ?1 AND cd.creado_en <= ?2 " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, fp.descripcion " +
                        "ORDER BY total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerEstadisticasFormaPagoPorFecha(LocalDateTime inicio, LocalDateTime fin);

        // Query con filtros de fecha y sucursal
        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "fp.descripcion, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "LEFT JOIN ( " +
                        "    SELECT cd2.forma_pago_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor * COALESCE(cd2.cambio, 1) WHEN cd2.vuelto = true THEN -ABS(cd2.valor * COALESCE(cd2.cambio, 1)) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "    AND cd.creado_en >= ?1 AND cd.creado_en <= ?2 " +
                        "    AND cd.sucursal_id = ?3 " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, fp.descripcion " +
                        "ORDER BY total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerEstadisticasFormaPagoPorFechaYSucursal(LocalDateTime inicio, LocalDateTime fin,
                        Long sucursalId);

        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "m.id as moneda_id, " +
                        "m.denominacion, " +
                        "m.simbolo, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "INNER JOIN ( " +
                        "    SELECT cd2.forma_pago_id, cd2.moneda_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor WHEN cd2.vuelto = true THEN -ABS(cd2.valor) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND cd2.moneda_id IS NOT NULL " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "INNER JOIN financiero.moneda m ON m.id = cd.moneda_id " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, m.id, m.denominacion, m.simbolo " +
                        "ORDER BY fp.id, total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerDesgloseMonedaFormaPago();

        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "m.id as moneda_id, " +
                        "m.denominacion, " +
                        "m.simbolo, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "INNER JOIN ( " +
                        "    SELECT cd2.forma_pago_id, cd2.moneda_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor WHEN cd2.vuelto = true THEN -ABS(cd2.valor) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND cd2.moneda_id IS NOT NULL " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id AND cd.sucursal_id = ?1 " +
                        "INNER JOIN financiero.moneda m ON m.id = cd.moneda_id " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, m.id, m.denominacion, m.simbolo " +
                        "ORDER BY fp.id, total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerDesgloseMonedaFormaPagoPorSucursal(Long sucursalId);

        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "m.id as moneda_id, " +
                        "m.denominacion, " +
                        "m.simbolo, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "INNER JOIN ( " +
                        "    SELECT cd2.forma_pago_id, cd2.moneda_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor WHEN cd2.vuelto = true THEN -ABS(cd2.valor) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND cd2.moneda_id IS NOT NULL " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "    AND cd.creado_en >= ?1 AND cd.creado_en <= ?2 " +
                        "INNER JOIN financiero.moneda m ON m.id = cd.moneda_id " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, m.id, m.denominacion, m.simbolo " +
                        "ORDER BY fp.id, total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerDesgloseMonedaFormaPagoPorFecha(LocalDateTime inicio, LocalDateTime fin);

        @Query(value = "SELECT fp.id as forma_pago_id, " +
                        "m.id as moneda_id, " +
                        "m.denominacion, " +
                        "m.simbolo, " +
                        "COUNT(DISTINCT (cd.venta_id, cd.sucursal_id)) FILTER (WHERE cd.venta_id IS NOT NULL) as cantidad_transacciones, " +
                        "COALESCE(SUM(cd.monto), 0) as total_monto " +
                        "FROM financiero.forma_pago fp " +
                        "INNER JOIN ( " +
                        "    SELECT cd2.forma_pago_id, cd2.moneda_id, v2.id as venta_id, v2.sucursal_id, v2.creado_en, " +
                        "    CASE WHEN cd2.pago = true THEN cd2.valor WHEN cd2.vuelto = true THEN -ABS(cd2.valor) ELSE 0 END as monto " +
                        "    FROM operaciones.cobro_detalle cd2 " +
                        "    JOIN operaciones.venta v2 ON v2.cobro_id = cd2.cobro_id AND v2.sucursal_id = cd2.sucursal_id " +
                        "    WHERE v2.estado = 'CONCLUIDA' AND (cd2.pago = true OR cd2.vuelto = true) " +
                        "      AND cd2.moneda_id IS NOT NULL " +
                        "      AND NOT EXISTS ( " +
                        "        SELECT 1 FROM operaciones.cobro_detalle cd_bad " +
                        "        WHERE cd_bad.cobro_id = v2.cobro_id AND cd_bad.sucursal_id = v2.sucursal_id " +
                        "        AND ABS(cd_bad.valor * COALESCE(cd_bad.cambio, 1)) >= 2000000000 " +
                        "      ) " +
                        ") cd ON cd.forma_pago_id = fp.id " +
                        "    AND cd.creado_en >= ?1 AND cd.creado_en <= ?2 " +
                        "    AND cd.sucursal_id = ?3 " +
                        "INNER JOIN financiero.moneda m ON m.id = cd.moneda_id " +
                        "WHERE fp.activo = true " +
                        "GROUP BY fp.id, m.id, m.denominacion, m.simbolo " +
                        "ORDER BY fp.id, total_monto DESC", nativeQuery = true)
        List<Object[]> obtenerDesgloseMonedaFormaPagoPorFechaYSucursal(LocalDateTime inicio, LocalDateTime fin,
                        Long sucursalId);
}