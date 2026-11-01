package com.franco.dev.service.financiero;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Lectura de las reaperturas de una caja PDV (issue desktop #662). {@code pdv_caja_reapertura} la escribe
 * la filial y llega por replicacion, sin entidad JPA en el central. La PK es (id, sucursal_id): el filtro
 * va siempre por caja y sucursal.
 */
@Service
public class PdvCajaReaperturaConsulta {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PdvCajaReaperturaConsulta(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Getter
    @AllArgsConstructor
    public static class Fila {
        private final Long id;
        private final Long pdvCajaId;
        private final Long sucursalId;
        private final String motivo;
        private final Long usuarioId;
        private final String usuarioNombre;
        private final LocalDateTime fechaCierreAnterior;
        private final LocalDateTime creadoEn;
    }

    /** Reaperturas de la caja, de la mas vieja a la mas nueva. */
    public List<Fila> porCaja(Long cajaId, Long sucursalId) {
        if (cajaId == null || sucursalId == null) return Collections.emptyList();
        return jdbcTemplate.query(
                "SELECT r.id, r.pdv_caja_id, r.sucursal_id, r.motivo, r.usuario_id," +
                        " COALESCE(p.nombre, u.nickname) AS usuario_nombre," +
                        " r.fecha_cierre_anterior, r.creado_en" +
                        " FROM financiero.pdv_caja_reapertura r" +
                        " LEFT JOIN personas.usuario u ON u.id = r.usuario_id" +
                        " LEFT JOIN personas.persona p ON p.id = u.persona_id" +
                        " WHERE r.pdv_caja_id = ? AND r.sucursal_id = ?" +
                        " ORDER BY r.creado_en, r.id",
                (rs, n) -> {
                    long usuarioIdValor = rs.getLong("usuario_id");
                    Long usuarioId = rs.wasNull() ? null : usuarioIdValor;
                    return new Fila(rs.getLong("id"), rs.getLong("pdv_caja_id"), rs.getLong("sucursal_id"),
                            rs.getString("motivo"), usuarioId,
                            rs.getString("usuario_nombre"),
                            aFecha(rs.getTimestamp("fecha_cierre_anterior")),
                            aFecha(rs.getTimestamp("creado_en")));
                }, cajaId, sucursalId);
    }

    private static LocalDateTime aFecha(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }
}
