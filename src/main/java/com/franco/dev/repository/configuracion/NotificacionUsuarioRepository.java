package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.repository.HelperRepository;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionUsuarioRepository extends HelperRepository<NotificacionUsuario, Long> {

        @Query("select nu from NotificacionUsuario nu "
                        + "join fetch nu.notificacion n "
                        + "left join fetch nu.usuario u "
                        + "where nu.estadoEnvio = :estado "
                        + "order by nu.creadoEn asc")
        List<NotificacionUsuario> findBatchByEstado(@Param("estado") EstadoEnvio estado, Pageable pageable);

        List<NotificacionUsuario> findAllByTokenFcm(String tokenFcm);

        Page<NotificacionUsuario> findAllByUsuarioId(Long usuarioId, Pageable pageable);

        Page<NotificacionUsuario> findAllByUsuarioIdAndTokenFcm(Long usuarioId, String tokenFcm, Pageable pageable);

        @Query("SELECT nu FROM NotificacionUsuario nu WHERE nu.usuario.id = :usuarioId AND (:tokenFcm IS NULL OR nu.tokenFcm = :tokenFcm) AND (:estado IS NULL OR nu.estadoTablero = :estado) ORDER BY nu.creadoEn DESC")
        Page<NotificacionUsuario> findByUsuarioIdAndTokenFcmAndEstadoTablero(
                        @Param("usuarioId") Long usuarioId,
                        @Param("tokenFcm") String tokenFcm,
                        @Param("estado") EstadoNotificacionTablero estado,
                        Pageable pageable);

        @Query("SELECT nu FROM NotificacionUsuario nu WHERE nu.usuario.id = :usuarioId " +
                        "AND (:tokenFcm IS NULL OR nu.tokenFcm = :tokenFcm) " +
                        "AND (:estado IS NULL OR nu.estadoTablero = :estado) " +
                        "AND (:leida IS NULL OR nu.leida = :leida) " +
                        "ORDER BY nu.creadoEn DESC")
        Page<NotificacionUsuario> findByFilters(
                        @Param("usuarioId") Long usuarioId,
                        @Param("tokenFcm") String tokenFcm,
                        @Param("estado") EstadoNotificacionTablero estado,
                        @Param("leida") Boolean leida,
                        Pageable pageable);

        @Query("SELECT COUNT(nu) FROM NotificacionUsuario nu WHERE nu.usuario.id = :usuarioId " +
                        "AND (:tokenFcm IS NULL OR nu.tokenFcm = :tokenFcm) " +
                        "AND (:estado IS NULL OR nu.estadoTablero = :estado) " +
                        "AND (:leida IS NULL OR nu.leida = :leida)")
        Long countByFilters(
                        @Param("usuarioId") Long usuarioId,
                        @Param("tokenFcm") String tokenFcm,
                        @Param("estado") EstadoNotificacionTablero estado,
                        @Param("leida") Boolean leida);

        Long deleteByFechaEnvioBefore(LocalDateTime fecha);

        boolean existsByNotificacionIdAndEstadoEnvioIn(Long notificacionId, List<EstadoEnvio> estados);
}
