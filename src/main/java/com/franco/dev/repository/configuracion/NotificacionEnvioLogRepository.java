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

    /**
     * Cola de despacho, en orden FIFO por id.
     *
     * No ordenar por fechaEnvio: esa columna solo se completa cuando el envio
     * sale bien, asi que es NULL en todas las filas PENDIENTE que esta query
     * selecciona. Ordenar por una clave constante obliga a materializar y
     * ordenar la cola entera para devolver un lote, y cuando el backlog pasa a
     * ser la mayoria de la tabla el planner abandona el indice de estadoEnvio y
     * cae en seq scan. Por id el recorrido es indexado y corta al completar el
     * lote.
     */
    @Query("SELECT nel FROM NotificacionEnvioLog nel " +
           "JOIN FETCH nel.notificacion n " +
           "WHERE nel.estadoEnvio = :estado " +
           "ORDER BY nel.id ASC")
    List<NotificacionEnvioLog> findBatchByEstado(@Param("estado") EstadoEnvio estado, Pageable pageable);

    @Query("SELECT nel FROM NotificacionEnvioLog nel WHERE nel.notificacion.id = :notificacionId")
    List<NotificacionEnvioLog> findByNotificacionId(@Param("notificacionId") Long notificacionId);

    @Query("SELECT nel FROM NotificacionEnvioLog nel " +
           "WHERE nel.usuario.id = :usuarioId " +
           "ORDER BY nel.fechaEnvio DESC")
    List<NotificacionEnvioLog> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    boolean existsByNotificacionIdAndEstadoEnvioIn(Long notificacionId, List<EstadoEnvio> estados);
}

