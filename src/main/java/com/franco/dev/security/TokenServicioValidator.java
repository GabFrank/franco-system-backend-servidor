package com.franco.dev.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Valida un token de servicio ({@code frcsvc_...}, issue #579) en cada request.
 *
 * Va por JdbcTemplate y no por JPA: corre dentro del filtro de seguridad, antes del open-in-view, con
 * su propia conexion en autocommit. Todas las condiciones viven en el SQL y se evaluan en cada
 * request, asi que revocar, vencer, desactivar al usuario o darle el rol ADMIN corta el acceso en el
 * siguiente request. Un usuario superusuario nunca autentica por token: un bot no lleva llave maestra
 * (mismo criterio de comparacion que {@code TesoreriaSecurityService.hasAnyRole}).
 */
@Component
public class TokenServicioValidator {

    private static final Logger log = LoggerFactory.getLogger(TokenServicioValidator.class);

    static final String SQL_VALIDAR =
            "SELECT t.id, u.nickname "
            + "FROM personas.token_servicio t "
            + "JOIN personas.usuario u ON u.id = t.usuario_id "
            + "WHERE t.hash = ? "
            + "  AND t.revocado_en IS NULL "
            + "  AND t.expira_en > now() "
            + "  AND u.activo IS NOT FALSE "
            + "  AND upper(trim(u.nickname)) <> 'ADMIN' "
            + "  AND NOT EXISTS ("
            + "      SELECT 1 FROM personas.usuario_role ur "
            + "      JOIN personas.role r ON r.id = ur.role_id "
            + "      WHERE ur.user_id = u.id AND upper(trim(r.nombre)) = 'ADMIN')";

    /** A lo sumo una escritura cada 5 minutos por token, y sin esperar una fila tomada por otro. */
    static final String SQL_ULTIMO_USO =
            "UPDATE personas.token_servicio SET ultimo_uso_en = now() "
            + "WHERE id = (SELECT id FROM personas.token_servicio "
            + "            WHERE id = ? AND (ultimo_uso_en IS NULL OR ultimo_uso_en < now() - interval '5 minutes') "
            + "            FOR UPDATE SKIP LOCKED)";

    private final JdbcTemplate jdbc;

    public TokenServicioValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Un token valido de servicio y el usuario con el que autentica. */
    public static final class Valido {
        private final long id;
        private final String nickname;

        Valido(long id, String nickname) {
            this.id = id;
            this.nickname = nickname;
        }

        public long getId() { return id; }

        public String getNickname() { return nickname; }
    }

    /**
     * El usuario del token, o vacio si no vale (inexistente, revocado, vencido, usuario inactivo o
     * superusuario). Un error de base no autentica: sale como
     * {@link InternalAuthenticationServiceException}, que el filtro responde con 401.
     */
    public Optional<Valido> validar(String token) {
        if (!TokenServicioFormato.esTokenDeServicio(token)) return Optional.empty();
        String prefijo = TokenServicioFormato.prefijoVisible(token);
        List<Valido> filas;
        try {
            filas = jdbc.query(SQL_VALIDAR,
                    (rs, i) -> new Valido(rs.getLong("id"), rs.getString("nickname")),
                    TokenServicioFormato.hash(token));
        } catch (DataAccessException e) {
            log.error("Token de servicio {}: no se pudo validar contra la base", prefijo, e);
            throw new InternalAuthenticationServiceException("No se pudo validar el token de servicio", e);
        }
        if (filas.isEmpty()) {
            log.warn("Token de servicio {} rechazado: no existe, esta revocado o vencido, o su usuario no puede usarlo", prefijo);
            return Optional.empty();
        }
        Valido v = filas.get(0);
        registrarUso(v.getId());
        return Optional.of(v);
    }

    private void registrarUso(long id) {
        try {
            jdbc.update(SQL_ULTIMO_USO, id);
        } catch (DataAccessException e) {
            log.warn("Token de servicio {}: no se pudo registrar el ultimo uso", id, e);
        }
    }
}
