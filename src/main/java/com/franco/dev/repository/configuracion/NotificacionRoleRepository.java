package com.franco.dev.repository.configuracion;

import com.franco.dev.domain.configuracion.NotificacionRole;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRoleRepository extends HelperRepository<NotificacionRole, Long> {

    default Class<NotificacionRole> getEntityClass() {
        return NotificacionRole.class;
    }

    @Query("SELECT nr FROM NotificacionRole nr JOIN FETCH nr.role WHERE nr.notificacion.id = :notificacionId")
    List<NotificacionRole> findByNotificacionId(@Param("notificacionId") Long notificacionId);

    @Query("SELECT nr FROM NotificacionRole nr WHERE nr.role.id = :roleId")
    List<NotificacionRole> findByRoleId(@Param("roleId") Long roleId);
}
