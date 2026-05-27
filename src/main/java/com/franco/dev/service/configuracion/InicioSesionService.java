package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class InicioSesionService extends CrudService<InicioSesion, InicioSesionRepository, Long> {

    private final InicioSesionRepository repository;

    @Override
    public InicioSesionRepository getRepository() {
        return repository;
    }

    public List<InicioSesion> findAll() {
        return repository.findAll();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher publisher;

    @Override
    public InicioSesion save(InicioSesion entity) {
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setCreadoEn(LocalDateTime.now());
            if (entity.getIdDispositivo() != null && entity.getUsuario() != null) {
                List<InicioSesion> sesionesPrevias = repository.findByUsuarioIdAndIdDispositivoAndHoraFinIsNull(
                        entity.getUsuario().getId(), entity.getIdDispositivo());
                for (InicioSesion previa : sesionesPrevias) {
                    previa.setHoraFin(LocalDateTime.now());
                    repository.save(previa);
                }
            }
        }
        InicioSesion e = super.save(entity);

        if (isNew) {
            publisher.publishEvent(new com.franco.dev.fmc.event.InicioSesionCreadoEvent(this, e));
        }

        return e;
    }

    public Page<InicioSesion> findByUsuarioIdAndHoraFinIsNul(Long id, Long sucId, Pageable pageable) {
        if (sucId != null) {
            return repository.findByUsuarioIdAndSucursalIdAndHoraFinIsNullOrderByIdDesc(id, sucId, pageable);
        } else {
            return repository.findByUsuarioIdAndHoraFinIsNullOrderByIdDesc(id, pageable);
        }
    }

    @Override
    public InicioSesion saveAndSend(InicioSesion entity, Boolean recibir) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }

        InicioSesion e = super.save(entity);
        return e;
    }

    public List<InicioSesion> findActiveSessionsByUsuarioIds(Collection<Long> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByUsuarioIdInAndHoraFinIsNullOrderByIdDesc(usuarioIds);
    }

    public List<InicioSesion> findSessionsWithValidTokensByUsuarioIds(Collection<Long> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByUsuarioIdInWithValidTokens(usuarioIds);
    }

    /**
     * Obtiene todas las sesiones con tokens válidos (para envío masivo de
     * notificaciones)
     * 
     * @return Lista de sesiones con tokens válidos
     */
    public List<InicioSesion> findSessionsWithValidTokens() {
        return repository.findAllWithValidTokens();
    }

    @org.springframework.transaction.annotation.Transactional
    public void clearToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        repository.clearTokenByToken(token);
    }

    public void detectarYNotificarNuevoDispositivo(InicioSesion sesion,
            com.franco.dev.fmc.service.PushNotificationService pushNotificationService,
            com.franco.dev.fmc.service.NotificationTemplateService notificationTemplateService,
            com.franco.dev.service.empresarial.SucursalService sucursalService) {

        if (sesion == null) {
            return;
        }

        if (sesion.getUsuario() == null) {
            return;
        }

        try {
            boolean esDispositivoNuevo = sesion.getIdDispositivo() != null &&
                    !repository.existsByUsuarioIdAndIdDispositivo(
                            sesion.getUsuario().getId(), sesion.getIdDispositivo());

            String nombreSucursal = null;
            if (sesion.getSucursal() != null && sucursalService != null) {
                com.franco.dev.domain.empresarial.Sucursal sucursal = sucursalService
                        .findById(sesion.getSucursal().getId()).orElse(null);
                if (sucursal != null) {
                    nombreSucursal = sucursal.getNombre();
                }
            }
            if (esDispositivoNuevo) {
                String tipoDispositivo = sesion.getTipoDespositivo() != null
                        ? sesion.getTipoDespositivo().toString()
                        : "DESCONOCIDO";

                com.franco.dev.fmc.model.PushNotificationRequest requestSeguridad = notificationTemplateService
                        .nuevoDispositivoDetectado(tipoDispositivo, nombreSucursal);

                requestSeguridad.setUsuarioIds(java.util.Collections.singletonList(sesion.getUsuario().getId()));
                pushNotificationService.sendPushNotificationToToken(requestSeguridad);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Error al detectar/notificar inicio de sesión: " + e.getMessage());
            e.printStackTrace();
        }
    }
}