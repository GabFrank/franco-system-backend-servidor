package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionPreferenciaUsuario;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificacionPreferenciaUsuarioRepository
        extends HelperRepository<NotificacionPreferenciaUsuario, Long> {
    Optional<NotificacionPreferenciaUsuario> findByUsuarioIdAndTipoNotificacion(Long usuarioId,
            String tipoNotificacion);
}
