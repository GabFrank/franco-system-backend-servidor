package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionComentario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionComentarioRepository extends HelperRepository<NotificacionComentario, Long> {

    default Class<NotificacionComentario> getEntityClass() {
        return NotificacionComentario.class;
    }
    @Query("SELECT DISTINCT nc FROM NotificacionComentario nc " +
           "LEFT JOIN FETCH nc.usuario u " +
           "LEFT JOIN FETCH u.persona " +
           "LEFT JOIN FETCH nc.comentarioPadre cp " +
           "LEFT JOIN FETCH cp.usuario cpu " +
           "WHERE nc.notificacion.id = :notificacionId " +
           "ORDER BY nc.creadoEn ASC")
    List<NotificacionComentario> findByNotificacionId(@Param("notificacionId") Long notificacionId);

    @Query("SELECT COUNT(nc) FROM NotificacionComentario nc " +
           "WHERE nc.notificacion.id = :notificacionId")
    Long countByNotificacionId(@Param("notificacionId") Long notificacionId);

    @Query("SELECT nc.notificacion.id, COUNT(nc) FROM NotificacionComentario nc " +
           "WHERE nc.notificacion.id IN :notificacionIds " +
           "GROUP BY nc.notificacion.id")
    List<Object[]> countByNotificacionIds(@Param("notificacionIds") List<Long> notificacionIds);
}

