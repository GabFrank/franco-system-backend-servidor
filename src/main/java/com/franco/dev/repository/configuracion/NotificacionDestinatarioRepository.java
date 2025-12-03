package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
        @Param("usuarioId") Long usuarioId
    );

    @Query(value = "SELECT nd FROM NotificacionDestinatario nd " +
           "JOIN FETCH nd.notificacion n " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND (:leida IS NULL OR nd.leida = :leida) " +
           "ORDER BY n.creadoEn DESC",
           countQuery = "SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND (:leida IS NULL OR nd.leida = :leida)")
    Page<NotificacionDestinatario> findByUsuarioId(
        @Param("usuarioId") Long usuarioId,
        @Param("leida") Boolean leida,
        Pageable pageable
    );

    @Query(value = "SELECT nd FROM NotificacionDestinatario nd " +
           "JOIN FETCH nd.notificacion n " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
           "AND (:leida IS NULL OR nd.leida = :leida) " +
           "ORDER BY n.creadoEn DESC",
           countQuery = "SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
           "JOIN nd.notificacion n " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
           "AND (:leida IS NULL OR nd.leida = :leida)")
    Page<NotificacionDestinatario> findByUsuarioIdAndFilters(
        @Param("usuarioId") Long usuarioId,
        @Param("estadoTablero") String estadoTablero,
        @Param("leida") Boolean leida,
        Pageable pageable
    );

    @Query("SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
           "JOIN nd.notificacion n " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND (:estadoTablero IS NULL OR n.estadoTablero = CAST(:estadoTablero AS text)) " +
           "AND (:leida IS NULL OR nd.leida = :leida)")
    Long countByUsuarioIdAndFilters(
        @Param("usuarioId") Long usuarioId,
        @Param("estadoTablero") String estadoTablero,
        @Param("leida") Boolean leida
    );

    @Query("SELECT COUNT(nd) FROM NotificacionDestinatario nd " +
           "WHERE nd.usuario.id = :usuarioId " +
           "AND nd.leida = false")
    Long countNoLeidasByUsuarioId(@Param("usuarioId") Long usuarioId);
}

