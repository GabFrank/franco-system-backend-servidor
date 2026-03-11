package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionTipoRole;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionTipoRoleRepository extends HelperRepository<NotificacionTipoRole, Long> {
    List<NotificacionTipoRole> findByRoleId(Long roleId);

    List<NotificacionTipoRole> findByRoleIdIn(List<Long> roleIds);

    List<NotificacionTipoRole> findByTipoNotificacion(String tipoNotificacion);
}
