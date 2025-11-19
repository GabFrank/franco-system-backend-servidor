package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.repository.HelperRepository;
import java.util.List;
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

    boolean existsByNotificacionIdAndEstadoEnvioIn(Long notificacionId, List<EstadoEnvio> estados);
}


