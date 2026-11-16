package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.ProductoRechazadoDto;
import com.franco.dev.utilitarios.Paginacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

/**
 * Lista de productos rechazados, para la pantalla "Productos rechazados" del desktop. Solo lectura.
 *
 * Junta tres fuentes en una sola consulta (UNION ALL), ordenada por fecha descendente:
 * <ol>
 *   <li><b>Transferencia</b>: ítems con motivo de rechazo en preparación, transporte o recepción.
 *       Al rechazar, el desktop copia el motivo a las etapas siguientes, así que la etapa que se
 *       informa es la primera con motivo, y la cantidad/presentación son las de esa etapa. Se
 *       excluyen las transferencias canceladas.</li>
 *   <li><b>Compra, recepción física</b>: ítems de la recepción de mercadería con cantidad rechazada.</li>
 *   <li><b>Compra, recepción documental</b>: ítems de nota en estado RECHAZADO.</li>
 * </ol>
 *
 * Va por JdbcTemplate porque cruza tablas de tres entidades distintas y no hay una entidad dueña
 * del resultado. Los parámetros llevan tipo explícito: Postgres no puede inferir el tipo de un
 * {@code :param IS NULL} con valor nulo.
 */
@Service
public class ProductoRechazadoService {

    public static final String FUENTE_TRANSFERENCIA = "TRANSFERENCIA";
    public static final String FUENTE_COMPRA = "COMPRA";

    private static final String FILTRO_TEXTO =
            " AND (CAST(:texto AS text) IS NULL OR UPPER(pr.descripcion) LIKE CONCAT('%', UPPER(CAST(:texto AS text)), '%')) ";

    private static final String UNION =
            "SELECT 'TRANSFERENCIA' AS fuente, " +
            "  CASE WHEN ti.motivo_rechazo_preparacion IS NOT NULL THEN 'PREPARACION' " +
            "       WHEN ti.motivo_rechazo_transporte IS NOT NULL THEN 'TRANSPORTE' ELSE 'RECEPCION' END AS etapa, " +
            "  t.id AS referencia_id, ti.id AS item_id, COALESCE(t.creado_en, ti.creado_en) AS fecha, " +
            "  pr.id AS producto_id, pr.descripcion AS producto_descripcion, pre.descripcion AS presentacion, " +
            "  CASE WHEN ti.motivo_rechazo_preparacion IS NOT NULL THEN COALESCE(ti.cantidad_preparacion, ti.cantidad_pre_transferencia) " +
            "       WHEN ti.motivo_rechazo_transporte IS NOT NULL THEN COALESCE(ti.cantidad_transporte, ti.cantidad_preparacion, ti.cantidad_pre_transferencia) " +
            "       ELSE COALESCE(ti.cantidad_recepcion, ti.cantidad_transporte, ti.cantidad_preparacion, ti.cantidad_pre_transferencia) END AS cantidad, " +
            "  CAST(COALESCE(ti.motivo_rechazo_preparacion, ti.motivo_rechazo_transporte, ti.motivo_rechazo_recepcion) AS text) AS motivo, " +
            "  CASE WHEN ti.motivo_rechazo_preparacion IS NOT NULL THEN ti.observacion_preparacion " +
            "       WHEN ti.motivo_rechazo_transporte IS NOT NULL THEN ti.observacion_transporte ELSE ti.observacion_recepcion END AS observacion, " +
            "  so.nombre AS origen, sd.nombre AS destino, u.nickname AS usuario " +
            "FROM operaciones.transferencia_item ti " +
            "JOIN operaciones.transferencia t ON t.id = ti.transferencia_id " +
            "JOIN productos.presentacion pre ON pre.id = CASE " +
            "     WHEN ti.motivo_rechazo_preparacion IS NOT NULL THEN COALESCE(ti.presentacion_preparacion_id, ti.presentacion_pre_transferencia_id) " +
            "     WHEN ti.motivo_rechazo_transporte IS NOT NULL THEN COALESCE(ti.presentacion_transporte_id, ti.presentacion_preparacion_id, ti.presentacion_pre_transferencia_id) " +
            "     ELSE COALESCE(ti.presentacion_recepcion_id, ti.presentacion_transporte_id, ti.presentacion_preparacion_id, ti.presentacion_pre_transferencia_id) END " +
            "JOIN productos.producto pr ON pr.id = pre.producto_id " +
            "LEFT JOIN empresarial.sucursal so ON so.id = t.sucursal_origen_id " +
            "LEFT JOIN empresarial.sucursal sd ON sd.id = t.sucursal_destino_id " +
            "LEFT JOIN personas.usuario u ON u.id = CASE " +
            "     WHEN ti.motivo_rechazo_preparacion IS NOT NULL THEN t.usuario_preparacion_id " +
            "     WHEN ti.motivo_rechazo_transporte IS NOT NULL THEN t.usuario_transporte_id ELSE t.usuario_recepcion_id END " +
            "WHERE COALESCE(ti.motivo_rechazo_preparacion, ti.motivo_rechazo_transporte, ti.motivo_rechazo_recepcion) IS NOT NULL " +
            "  AND (t.estado IS NULL OR t.estado <> 'CANCELADA') " +
            "  AND (CAST(:fuente AS text) IS NULL OR CAST(:fuente AS text) = 'TRANSFERENCIA') " +
            "  AND (CAST(:desde AS date) IS NULL OR COALESCE(t.creado_en, ti.creado_en) >= CAST(:desde AS date)) " +
            "  AND (CAST(:hasta AS date) IS NULL OR COALESCE(t.creado_en, ti.creado_en) < CAST(:hasta AS date) + 1) " +
            "  AND (CAST(:sucursalId AS bigint) IS NULL OR t.sucursal_origen_id = CAST(:sucursalId AS bigint) " +
            "       OR t.sucursal_destino_id = CAST(:sucursalId AS bigint)) " +
            "  AND (CAST(:referenciaId AS bigint) IS NULL OR t.id = CAST(:referenciaId AS bigint)) " +
            FILTRO_TEXTO +
            "UNION ALL " +
            "SELECT 'COMPRA', 'RECEPCION_FISICA', nr.pedido_id, rmi.id, rm.fecha, " +
            "  pr.id, pr.descripcion, pre.descripcion, rmi.cantidad_rechazada, CAST(rmi.motivo_rechazo AS text), " +
            "  rmi.observaciones, pp.nombre, s.nombre, u.nickname " +
            "FROM operaciones.recepcion_mercaderia_item rmi " +
            "JOIN operaciones.recepcion_mercaderia rm ON rm.id = rmi.recepcion_mercaderia_id " +
            "JOIN productos.producto pr ON pr.id = rmi.producto_id " +
            "LEFT JOIN productos.presentacion pre ON pre.id = rmi.presentacion_recibida_id " +
            "LEFT JOIN operaciones.nota_recepcion_item nri ON nri.id = rmi.nota_recepcion_item_id " +
            "LEFT JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id " +
            "LEFT JOIN personas.proveedor prov ON prov.id = rm.proveedor_id " +
            "LEFT JOIN personas.persona pp ON pp.id = prov.persona_id " +
            "LEFT JOIN empresarial.sucursal s ON s.id = rmi.sucursal_entrega_id " +
            "LEFT JOIN personas.usuario u ON u.id = rmi.usuario_id " +
            "WHERE COALESCE(rmi.cantidad_rechazada, 0) > 0 " +
            "  AND (CAST(:fuente AS text) IS NULL OR CAST(:fuente AS text) = 'COMPRA') " +
            "  AND (CAST(:desde AS date) IS NULL OR rm.fecha >= CAST(:desde AS date)) " +
            "  AND (CAST(:hasta AS date) IS NULL OR rm.fecha < CAST(:hasta AS date) + 1) " +
            "  AND (CAST(:sucursalId AS bigint) IS NULL OR rmi.sucursal_entrega_id = CAST(:sucursalId AS bigint)) " +
            "  AND (CAST(:referenciaId AS bigint) IS NULL OR nr.pedido_id = CAST(:referenciaId AS bigint)) " +
            FILTRO_TEXTO +
            "UNION ALL " +
            "SELECT 'COMPRA', 'RECEPCION_DOCUMENTAL', nr.pedido_id, nri.id, COALESCE(nri.creado_en, nr.creado_en), " +
            "  pr.id, pr.descripcion, pre.descripcion, nri.cantidad_en_nota, nri.motivo_rechazo, " +
            "  nri.observacion, pp.nombre, s.nombre, u.nickname " +
            "FROM operaciones.nota_recepcion_item nri " +
            "JOIN operaciones.nota_recepcion nr ON nr.id = nri.nota_recepcion_id " +
            "JOIN productos.producto pr ON pr.id = nri.producto_id " +
            "LEFT JOIN productos.presentacion pre ON pre.id = nri.presentacion_en_nota_id " +
            "LEFT JOIN operaciones.pedido ped ON ped.id = nr.pedido_id " +
            "LEFT JOIN personas.proveedor prov ON prov.id = ped.proveedor_id " +
            "LEFT JOIN personas.persona pp ON pp.id = prov.persona_id " +
            "LEFT JOIN empresarial.sucursal s ON s.id = nr.sucursal_id " +
            "LEFT JOIN personas.usuario u ON u.id = nri.usuario_id " +
            "WHERE nri.estado = 'RECHAZADO' " +
            "  AND (CAST(:fuente AS text) IS NULL OR CAST(:fuente AS text) = 'COMPRA') " +
            "  AND (CAST(:desde AS date) IS NULL OR COALESCE(nri.creado_en, nr.creado_en) >= CAST(:desde AS date)) " +
            "  AND (CAST(:hasta AS date) IS NULL OR COALESCE(nri.creado_en, nr.creado_en) < CAST(:hasta AS date) + 1) " +
            "  AND (CAST(:sucursalId AS bigint) IS NULL OR nr.sucursal_id = CAST(:sucursalId AS bigint)) " +
            "  AND (CAST(:referenciaId AS bigint) IS NULL OR nr.pedido_id = CAST(:referenciaId AS bigint)) " +
            FILTRO_TEXTO;

    private static final String SQL_PAGINA =
            "SELECT * FROM (" + UNION + ") r " +
            "ORDER BY r.fecha DESC NULLS LAST, r.referencia_id DESC NULLS LAST, r.item_id DESC " +
            "LIMIT :limite OFFSET :desplazamiento";

    private static final String SQL_TOTAL = "SELECT COUNT(*) FROM (" + UNION + ") r";

    private static final RowMapper<ProductoRechazadoDto> MAPPER = (rs, i) -> {
        ProductoRechazadoDto d = new ProductoRechazadoDto();
        d.setFuente(rs.getString("fuente"));
        d.setEtapa(rs.getString("etapa"));
        d.setReferenciaId(rs.getObject("referencia_id") != null ? rs.getLong("referencia_id") : null);
        d.setItemId(rs.getLong("item_id"));
        Timestamp fecha = rs.getTimestamp("fecha");
        d.setFecha(fecha != null ? fecha.toLocalDateTime() : null);
        d.setProductoId(rs.getLong("producto_id"));
        d.setProductoDescripcion(rs.getString("producto_descripcion"));
        d.setPresentacion(rs.getString("presentacion"));
        d.setCantidad(rs.getObject("cantidad") != null ? rs.getDouble("cantidad") : null);
        d.setMotivo(rs.getString("motivo"));
        d.setObservacion(rs.getString("observacion"));
        d.setOrigen(rs.getString("origen"));
        d.setDestino(rs.getString("destino"));
        d.setUsuario(rs.getString("usuario"));
        return d;
    };

    private final NamedParameterJdbcTemplate jdbc;

    public ProductoRechazadoService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param fuente       TRANSFERENCIA, COMPRA o null para las dos
     * @param desde        fecha inicial inclusiva, o null
     * @param hasta        fecha final inclusiva (todo ese día), o null
     * @param sucursalId   transferencia: origen o destino; compra: sucursal que recibía
     * @param referenciaId nro. de transferencia o de pedido
     * @param texto        parte de la descripción del producto
     */
    public Page<ProductoRechazadoDto> buscar(String fuente, LocalDate desde, LocalDate hasta, Long sucursalId,
                                             Long referenciaId, String texto, Integer page, Integer size) {
        Pageable pageable = Paginacion.of(page, size);
        String fuenteNormalizada = normalizarFuente(fuente);
        String textoNormalizado = texto != null && !texto.trim().isEmpty() ? texto.trim() : null;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fuente", fuenteNormalizada, Types.VARCHAR)
                .addValue("desde", desde != null ? java.sql.Date.valueOf(desde) : null, Types.DATE)
                .addValue("hasta", hasta != null ? java.sql.Date.valueOf(hasta) : null, Types.DATE)
                .addValue("sucursalId", sucursalId, Types.BIGINT)
                .addValue("referenciaId", referenciaId, Types.BIGINT)
                .addValue("texto", textoNormalizado, Types.VARCHAR);

        Long total = jdbc.queryForObject(SQL_TOTAL, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        params.addValue("limite", pageable.getPageSize(), Types.INTEGER)
                .addValue("desplazamiento", pageable.getOffset(), Types.BIGINT);
        List<ProductoRechazadoDto> contenido = jdbc.query(SQL_PAGINA, params, MAPPER);
        return new PageImpl<>(contenido, pageable, total);
    }

    /** Un valor desconocido se trata como "todas", no como un filtro que no devuelve nada. */
    static String normalizarFuente(String fuente) {
        if (fuente == null) return null;
        String f = fuente.trim().toUpperCase();
        return FUENTE_TRANSFERENCIA.equals(f) || FUENTE_COMPRA.equals(f) ? f : null;
    }
}
