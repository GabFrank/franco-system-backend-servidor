package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionTipoEstado;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacionTipoEstadoRepository extends HelperRepository<NotificacionTipoEstado, Long> {
    Optional<NotificacionTipoEstado> findByTipoNotificacion(String tipoNotificacion);

    List<NotificacionTipoEstado> findAllByOrderByTipoNotificacionAsc();
}
