package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.EntradaSinLoteDto;
import com.franco.dev.domain.operaciones.dto.LoteEncontradoDto;
import com.franco.dev.domain.operaciones.dto.ResultadoBusquedaLoteDto;
import com.franco.dev.domain.operaciones.dto.TrazabilidadProductoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

/**
 * Buscar un lote por su numero para responder un reclamo (#429, fase 2, entrega 1).
 *
 * A diferencia de "Stock por lotes", parte del MAESTRO operaciones.lote: un lote vendido entero
 * (saldo 0) o archivado tambien aparece, que es justamente el caso del reclamo.
 *
 * La comparacion usa una clave tolerante (ver {@link #claveBusqueda(String)}) que tiene su gemela
 * en SQL, operaciones.clave_busqueda_lote (V255.6), indexada para la coincidencia exacta.
 */
@Service
public class BusquedaLoteService {

    /** Tope de coincidencias: la pantalla es para encontrar un lote, no para listar el maestro. */
    static final int MAX_RESULTADOS = 20;
    /** Por debajo de esto la coincidencia parcial devolveria medio maestro. */
    static final int MIN_CARACTERES_PARCIAL = 3;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Clave de busqueda: mayusculas, solo letras y digitos, sin ceros a la izquierda. "3497",
     * "03497", "03 497" y "lote-03497" dan "3497" / "LOTE3497". Si todo eran ceros ("000"), se
     * devuelve sin quitarlos para no buscar la cadena vacia. Nulo si no queda nada.
     *
     * Tiene que coincidir con operaciones.clave_busqueda_lote en V255.6.
     */
    public static String claveBusqueda(String numero) {
        if (numero == null) return null;
        String limpio = numero.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (limpio.isEmpty()) return null;
        String sinCeros = limpio.replaceFirst("^0+", "");
        return sinCeros.isEmpty() ? limpio : sinCeros;
    }

    private static final String SELECT_LOTE =
            "SELECT l.id AS lote_id, l.producto_id, p.descripcion AS producto_descripcion, " +
            "       l.numero_lote, l.fecha_vencimiento, l.estado, pp.nombre AS proveedor_nombre, " +
            "       (la.lote_id IS NOT NULL) AS archivado, " +
            "       ent.creado_en AS primera_entrada_fecha, ent.tipo AS primera_entrada_tipo, " +
            "       ent.documento_id AS primera_entrada_documento_id, " +
            "       COALESCE(agg.saldo, 0) AS saldo_total, COALESCE(agg.vendido, 0) AS total_vendido, " +
            "       COALESCE(agg.ventas, 0) AS cantidad_ventas " +
            "FROM operaciones.lote l " +
            "JOIN productos.producto p ON p.id = l.producto_id " +
            "LEFT JOIN personas.proveedor pr ON pr.id = l.proveedor_id " +
            "LEFT JOIN personas.persona pp ON pp.id = pr.persona_id " +
            "LEFT JOIN operaciones.lote_archivado la ON la.lote_id = l.id " +
            "LEFT JOIN LATERAL ( " +
            "    SELECT CAST(SUM(msl.cantidad) AS double precision) AS saldo, " +
            "           CAST(-SUM(CASE WHEN CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "                          THEN msl.cantidad ELSE 0 END) AS double precision) AS vendido, " +
            "           COUNT(DISTINCT CASE WHEN CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "                 THEN CAST(vi.venta_id AS text) || '-' || CAST(vi.sucursal_id AS text) END) AS ventas " +
            "    FROM operaciones.movimiento_stock_lote msl " +
            "    LEFT JOIN operaciones.movimiento_stock ms " +
            "           ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "    LEFT JOIN operaciones.venta_item vi " +
            "           ON CAST(ms.tipo_movimiento AS text) = 'VENTA' " +
            "          AND vi.id = ms.referencia AND vi.sucursal_id = ms.sucursal_id " +
            "    WHERE msl.lote_id = l.id AND msl.estado = true) agg ON true " +
            "LEFT JOIN LATERAL ( " +
            "    SELECT msl.creado_en, CAST(ms.tipo_movimiento AS text) AS tipo, " +
            "           COALESCE(rmi.recepcion_mercaderia_id, ti.transferencia_id, ms.referencia) AS documento_id " +
            "    FROM operaciones.movimiento_stock_lote msl " +
            "    LEFT JOIN operaciones.movimiento_stock ms " +
            "           ON ms.id = msl.movimiento_stock_id AND ms.sucursal_id = msl.sucursal_id " +
            "    LEFT JOIN operaciones.recepcion_mercaderia_item rmi " +
            "           ON CAST(ms.tipo_movimiento AS text) = 'COMPRA' AND rmi.id = ms.referencia " +
            "    LEFT JOIN operaciones.transferencia_item ti " +
            "           ON CAST(ms.tipo_movimiento AS text) = 'TRANSFERENCIA' AND ti.id = ms.referencia " +
            "    WHERE msl.lote_id = l.id AND msl.estado = true AND msl.cantidad > 0 " +
            "    ORDER BY msl.creado_en ASC, msl.id ASC LIMIT 1) ent ON true ";

    private static final String ORDEN =
            " ORDER BY l.fecha_vencimiento DESC NULLS LAST, l.id DESC LIMIT " + MAX_RESULTADOS;

    @Transactional(readOnly = true)
    public ResultadoBusquedaLoteDto buscarLotePorNumero(String numeroLote, Long productoId) {
        ResultadoBusquedaLoteDto r = new ResultadoBusquedaLoteDto();
        r.setNumeroBuscado(numeroLote);
        String clave = claveBusqueda(numeroLote);
        r.setClaveBusqueda(clave);
        r.setEncontrado(false);
        r.setCoincidenciaExacta(false);
        if (clave == null) return r;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clave", clave)
                .addValue("productoId", productoId, java.sql.Types.BIGINT);
        String filtroProducto = " AND (CAST(:productoId AS bigint) IS NULL OR l.producto_id = :productoId) ";

        List<LoteEncontradoDto> lotes = jdbc.query(SELECT_LOTE +
                        "WHERE operaciones.clave_busqueda_lote(l.numero_lote) = :clave" + filtroProducto + ORDEN,
                params, (rs, i) -> mapLote(rs));
        if (!lotes.isEmpty()) {
            r.setCoincidenciaExacta(true);
        } else if (clave.length() >= MIN_CARACTERES_PARCIAL) {
            lotes = jdbc.query(SELECT_LOTE +
                            "WHERE operaciones.clave_busqueda_lote(l.numero_lote) LIKE '%' || :clave || '%'" +
                            filtroProducto + ORDEN,
                    params, (rs, i) -> mapLote(rs));
        }
        r.setLotes(lotes);
        r.setEncontrado(!lotes.isEmpty());
        if (!r.getEncontrado() && productoId != null) {
            r.setTrazabilidad(trazabilidad(productoId));
        }
        return r;
    }

    /**
     * Desde cuando se controla el lote de un producto y que entro sin lote desde entonces.
     */
    @Transactional(readOnly = true)
    public TrazabilidadProductoDto trazabilidad(Long productoId) {
        MapSqlParameterSource params = new MapSqlParameterSource("productoId", productoId);
        TrazabilidadProductoDto t = new TrazabilidadProductoDto();
        t.setProductoId(productoId);
        t.setProductoDescripcion(jdbc.query(
                "SELECT descripcion FROM productos.producto WHERE id = :productoId", params,
                rs -> rs.next() ? rs.getString(1) : null));

        // Solo cuenta un lote REAL: una fila del bucket SIN LOTE no prueba que hubiera control.
        Timestamp desde = jdbc.query(
                "SELECT MIN(msl.creado_en) FROM operaciones.movimiento_stock_lote msl " +
                "WHERE msl.producto_id = :productoId AND msl.lote_id IS NOT NULL AND msl.estado = true",
                params, rs -> rs.next() ? rs.getTimestamp(1) : null);
        if (desde == null) {
            t.setComprasAnteriores(0L);
            t.setTodasLasEntradasConLote(false);
            return t;
        }
        t.setTrazadoDesde(desde.toLocalDateTime());
        params.addValue("desde", desde);

        // Toda entrada (movimiento con cantidad positiva: compra, transferencia recibida, ajuste,
        // devolucion...) desde el trazado cuya cantidad no quedo cubierta por filas con lote real.
        t.setEntradasSinLote(jdbc.query(
                "SELECT x.tipo, COUNT(*) AS movimientos, " +
                "       CAST(SUM(x.cantidad - x.cubierto) AS double precision) AS sin_lote " +
                "FROM ( " +
                "   SELECT CAST(ms.tipo_movimiento AS text) AS tipo, ms.cantidad, " +
                "          COALESCE((SELECT SUM(msl.cantidad) FROM operaciones.movimiento_stock_lote msl " +
                "                    WHERE msl.movimiento_stock_id = ms.id AND msl.sucursal_id = ms.sucursal_id " +
                "                      AND msl.lote_id IS NOT NULL AND msl.estado = true), 0) AS cubierto " +
                "   FROM operaciones.movimiento_stock ms " +
                "   WHERE ms.producto_id = :productoId AND ms.estado = true AND ms.cantidad > 0 " +
                "     AND CAST(ms.creado_en AS timestamp) >= :desde) x " +
                "WHERE x.cubierto < x.cantidad " +
                "GROUP BY x.tipo ORDER BY x.tipo",
                params, (rs, i) -> new EntradaSinLoteDto(rs.getString(1), rs.getLong(2), rs.getDouble(3))));

        t.setComprasAnteriores(jdbc.queryForObject(
                "SELECT COUNT(*) FROM operaciones.movimiento_stock ms " +
                "WHERE ms.producto_id = :productoId AND ms.estado = true AND ms.cantidad > 0 " +
                "  AND CAST(ms.tipo_movimiento AS text) = 'COMPRA' " +
                "  AND CAST(ms.creado_en AS timestamp) < :desde",
                params, Long.class));

        t.setTodasLasEntradasConLote(t.getEntradasSinLote().isEmpty() && t.getComprasAnteriores() == 0);
        return t;
    }

    private static LoteEncontradoDto mapLote(ResultSet rs) throws SQLException {
        LoteEncontradoDto d = new LoteEncontradoDto();
        d.setLoteId(rs.getLong("lote_id"));
        d.setProductoId(rs.getLong("producto_id"));
        d.setProductoDescripcion(rs.getString("producto_descripcion"));
        d.setNumeroLote(rs.getString("numero_lote"));
        java.sql.Date venc = rs.getDate("fecha_vencimiento");
        d.setFechaVencimiento(venc != null ? venc.toLocalDate() : null);
        d.setEstado(rs.getString("estado"));
        d.setProveedorNombre(rs.getString("proveedor_nombre"));
        d.setArchivado(rs.getBoolean("archivado"));
        Timestamp ent = rs.getTimestamp("primera_entrada_fecha");
        d.setPrimeraEntradaFecha(ent != null ? ent.toLocalDateTime() : null);
        d.setPrimeraEntradaTipo(rs.getString("primera_entrada_tipo"));
        long doc = rs.getLong("primera_entrada_documento_id");
        d.setPrimeraEntradaDocumentoId(rs.wasNull() ? null : doc);
        d.setSaldoTotal(rs.getDouble("saldo_total"));
        d.setTotalVendido(rs.getDouble("total_vendido"));
        d.setCantidadVentas(rs.getLong("cantidad_ventas"));
        return d;
    }
}
