package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionEnvioLog;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionEnvioLogRepository extends HelperRepository<NotificacionEnvioLog, Long> {

    default Class<NotificacionEnvioLog> getEntityClass() {
        return NotificacionEnvioLog.class;
    }

    @Query("SELECT nel FROM NotificacionEnvioLog nel " +
           "JOIN FETCH nel.notificacion n " +
           "WHERE nel.estadoEnvio = :estado " +
           "ORDER BY nel.fechaEnvio ASC")
    List<NotificacionEnvioLog> findBatchByEstado(@Param("estado") EstadoEnvio estado, Pageable pageable);

    @Query("SELECT nel FROM NotificacionEnvioLog nel WHERE nel.notificacion.id = :notificacionId")
    List<NotificacionEnvioLog> findByNotificacionId(@Param("notificacionId") Long notificacionId);

    @Query("SELECT nel FROM NotificacionEnvioLog nel " +
           "WHERE nel.usuario.id = :usuarioId " +
           "ORDER BY nel.fechaEnvio DESC")
    List<NotificacionEnvioLog> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    boolean existsByNotificacionIdAndEstadoEnvioIn(Long notificacionId, List<EstadoEnvio> estados);
}

