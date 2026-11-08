package com.franco.dev.service.operaciones;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lectura de participaciones de un sorteo. {@code sorteo_participacion} la escriben las filiales y llega
 * por replicacion; no guarda estado: una participacion esta ANULADA si su venta esta CANCELADA o si la
 * venta todavia no existe en el central (puede haber filas cuya venta nunca se confirmo).
 * La PK de venta es compuesta, por eso el join va por id y sucursal_id.
 */
@Service
public class SorteoParticipacionConsulta {

    private static final String FROM =
            " FROM operaciones.sorteo_participacion p" +
            " LEFT JOIN operaciones.venta v ON v.id = p.venta_id AND v.sucursal_id = p.sucursal_id" +
            " LEFT JOIN empresarial.sucursal s ON s.id = p.sucursal_id";
    private static final String ANULADA = "(v.id IS NULL OR v.estado = 'CANCELADA')";

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public SorteoParticipacionConsulta(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Getter
    @AllArgsConstructor
    public static class Fila {
        private final Long id;
        private final Long sucursalId;
        private final String sucursalNombre;
        private final Long ventaId;
        private final String codigo;
        private final Integer cantidad;
        private final String clienteNombre;
        private final String documento;
        private final Boolean anulada;
        private final LocalDateTime creadoEn;
    }

    @Getter
    @AllArgsConstructor
    public static class Resumen {
        private final Long sucursalId;
        private final String sucursalNombre;
        private final Integer participaciones;
        private final Integer tickets;
    }

    /**
     * Un codigo impreso en el ticket ("S3-2-1501-2/3") se reduce al codigo de la participacion
     * ("S3-2-1501"): se corta en el ultimo "-" solo si el sufijo tiene "/". Pasa a mayusculas.
     */
    public static String normalizarCodigo(String codigo) {
        if (codigo == null) return null;
        String c = codigo.trim().toUpperCase();
        if (c.isEmpty()) return null;
        if (esCodigoDeTicket(c)) c = c.substring(0, c.lastIndexOf('-'));
        return c;
    }

    /** True si el codigo termina en un sufijo de ticket "-n/m". */
    public static boolean esCodigoDeTicket(String codigo) {
        if (codigo == null) return false;
        int guion = codigo.lastIndexOf('-');
        return guion > 0 && codigo.indexOf('/', guion) > guion;
    }

    static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Page<Fila> listar(Long sorteoId, Long sucursalId, String codigo, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE p.sorteo_id = ?");
        args.add(sorteoId);
        if (sucursalId != null) {
            where.append(" AND p.sucursal_id = ?");
            args.add(sucursalId);
        }
        String normalizado = normalizarCodigo(codigo);
        if (normalizado != null) {
            if (esCodigoDeTicket(codigo.trim())) {
                // Ticket completo: coincidencia exacta, para que S3-2-1501 no traiga S3-2-15010.
                where.append(" AND UPPER(p.codigo) = ?");
                args.add(normalizado);
            } else {
                where.append(" AND UPPER(p.codigo) LIKE ? ESCAPE '\\'");
                args.add(escaparLike(normalizado) + "%");
            }
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + FROM + where, Long.class, args.toArray());
        List<Object> argsPagina = new ArrayList<>(args);
        argsPagina.add(size);
        argsPagina.add((long) page * size);
        List<Fila> filas = jdbcTemplate.query(
                "SELECT p.id, p.sucursal_id, s.nombre AS sucursal_nombre, p.venta_id, p.codigo, p.cantidad," +
                        " p.nombre AS cliente_nombre, p.documento, " + ANULADA + " AS anulada, p.creado_en" +
                        FROM + where + " ORDER BY p.creado_en DESC, p.id DESC, p.sucursal_id LIMIT ? OFFSET ?",
                (rs, n) -> {
                    Timestamp creado = rs.getTimestamp("creado_en");
                    return new Fila(rs.getLong("id"), rs.getLong("sucursal_id"), rs.getString("sucursal_nombre"),
                            rs.getLong("venta_id"), rs.getString("codigo"), rs.getInt("cantidad"),
                            rs.getString("cliente_nombre"), rs.getString("documento"), rs.getBoolean("anulada"),
                            creado != null ? creado.toLocalDateTime() : null);
                }, argsPagina.toArray());
        return new PageImpl<>(filas, PageRequest.of(page, size), total != null ? total : 0L);
    }

    /** Participaciones y tickets vigentes (no anulados) por sucursal. */
    public List<Resumen> resumen(Long sorteoId) {
        return jdbcTemplate.query(
                "SELECT p.sucursal_id, s.nombre AS sucursal_nombre, COUNT(*) AS participaciones," +
                        " COALESCE(SUM(p.cantidad), 0) AS tickets" +
                        FROM + " WHERE p.sorteo_id = ? AND NOT " + ANULADA +
                        " GROUP BY p.sucursal_id, s.nombre ORDER BY p.sucursal_id",
                (rs, n) -> new Resumen(rs.getLong("sucursal_id"), rs.getString("sucursal_nombre"),
                        rs.getInt("participaciones"), rs.getInt("tickets")),
                sorteoId);
    }
}
