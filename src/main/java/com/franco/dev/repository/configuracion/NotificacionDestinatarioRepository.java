package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificacionDestinatarioRepository extends HelperRepository<NotificacionDestinatario, Long> {

       default Class<NotificacionDestinatario> getEntityClass() {
              return NotificacionDestinatario.class;
       }

       @Query("SELECT nd FROM NotificacionDestinatario nd " +
                     "WHERE nd.notificacion.id = :notificacionId " +
                     "AND nd.usuario.id = :usuarioId")
       Optional<NotificacionDestinatario> findByNotificacionIdAndUsuarioId(
                     @Param("notificacionId") Long notificacionId,
                     @Param("usuarioId") Long usuarioId);

       @Query(value = "SELECT nd FROM NotificacionDestinatario nd " +
                     "JOIN FETCH nd.notificacion n " +
                     "WHERE nd.usuario.id = :usuarioId " +
                     "AND (:leida IS NULL OR nd.leida = :leida) " +
                     "ORDER BY n.creadoEn DESC", countQuery = "SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
                                   "WHERE nd.usuario.id = :usuarioId " +
                                   "AND (:leida IS NULL OR nd.leida = :leida)")
       Page<NotificacionDestinatario> findByUsuarioId(
                     @Param("usuarioId") Long usuarioId,
                     @Param("leida") Boolean leida,
                     Pageable pageable);

       @Query(value = "SELECT nd FROM NotificacionDestinatario nd " +
                     "JOIN FETCH nd.notificacion n " +
                     "WHERE nd.usuario.id = :usuarioId " +
                     "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
                     "AND (:leida IS NULL OR nd.leida = :leida) " +
                     "AND (CAST(:fechaInicio AS timestamp) IS NULL OR n.creadoEn >= CAST(:fechaInicio AS timestamp)) " +
                     "AND (CAST(:fechaFin AS timestamp) IS NULL OR n.creadoEn <= CAST(:fechaFin AS timestamp)) " +
                     "ORDER BY n.creadoEn DESC", countQuery = "SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
                                   "JOIN nd.notificacion n " +
                                   "WHERE nd.usuario.id = :usuarioId " +
                                   "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
                                   "AND (:leida IS NULL OR nd.leida = :leida) " +
                                   "AND (CAST(:fechaInicio AS timestamp) IS NULL OR n.creadoEn >= CAST(:fechaInicio AS timestamp)) " +
                                   "AND (CAST(:fechaFin AS timestamp) IS NULL OR n.creadoEn <= CAST(:fechaFin AS timestamp))")
       Page<NotificacionDestinatario> findByUsuarioIdAndFilters(
                     @Param("usuarioId") Long usuarioId,
                     @Param("estadoTablero") String estadoTablero,
                     @Param("leida") Boolean leida,
                     @Param("fechaInicio") java.time.LocalDateTime fechaInicio,
                     @Param("fechaFin") java.time.LocalDateTime fechaFin,
                     Pageable pageable);

       @Query("SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
                     "JOIN nd.notificacion n " +
                     "WHERE nd.usuario.id = :usuarioId " +
                     "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
                     "AND (:leida IS NULL OR nd.leida = :leida) " +
                     "AND (CAST(:fechaInicio AS timestamp) IS NULL OR n.creadoEn >= CAST(:fechaInicio AS timestamp)) " +
                     "AND (CAST(:fechaFin AS timestamp) IS NULL OR n.creadoEn <= CAST(:fechaFin AS timestamp))")
       Long countByUsuarioIdAndFilters(
                     @Param("usuarioId") Long usuarioId,
                     @Param("estadoTablero") String estadoTablero,
                     @Param("leida") Boolean leida,
                     @Param("fechaInicio") java.time.LocalDateTime fechaInicio,
                     @Param("fechaFin") java.time.LocalDateTime fechaFin);

       @Query("SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
                     "WHERE nd.usuario.id = :usuarioId " +
                     "AND nd.leida = false")
       Long countNoLeidasByUsuarioId(@Param("usuarioId") Long usuarioId);

       @Query("SELECT DISTINCT nd FROM NotificacionDestinatario nd " +
                     "JOIN FETCH nd.usuario u " +
                     "LEFT JOIN FETCH u.persona p " +
                     "WHERE nd.notificacion.id = :notificacionId " +
                     "ORDER BY p.nombre ASC, u.nickname ASC")
       List<NotificacionDestinatario> findNotificacionesDestinatariosByNotificacionId(
                     @Param("notificacionId") Long notificacionId);

       @Query(value = "WITH roles_notificacion AS (" +
                     "    SELECT DISTINCT role_id " +
                     "    FROM configuraciones.notificacion_role " +
                     "    WHERE notificacion_id = :notificacionId" +
                     ")," +
                     "usuarios_por_roles AS (" +
                     "    SELECT DISTINCT COALESCE(ur.user_id, ur.usuario_id) AS usuario_id" +
                     "    FROM personas.usuario_role ur" +
                     "    WHERE ur.role_id IN (SELECT role_id FROM roles_notificacion)" +
                     "      AND (ur.user_id IS NOT NULL OR ur.usuario_id IS NOT NULL)" +
                     ")," +
                     "usuarios_destinatarios AS (" +
                     "    SELECT DISTINCT usuario_id" +
                     "    FROM configuraciones.notificacion_destinatario" +
                     "    WHERE notificacion_id = :notificacionId" +
                     ")" +
                     "SELECT DISTINCT u.id " +
                     "FROM personas.usuario u " +
                     "WHERE u.id IN (" +
                     "    SELECT usuario_id FROM usuarios_por_roles" +
                     "    UNION" +
                     "    SELECT usuario_id FROM usuarios_destinatarios" +
                     ") " +
                     "ORDER BY u.id", nativeQuery = true)
       List<Long> findUsuarioIdsConAccesoNotificacion(@Param("notificacionId") Long notificacionId);

}
