package com.franco.dev.service.configuracion;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.repository.configuracion.InicioSesionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@AllArgsConstructor
public class InicioSesionService extends CrudService<InicioSesion, InicioSesionRepository, EmbebedPrimaryKey> {

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
    @org.springframework.transaction.annotation.Transactional
    public InicioSesion save(InicioSesion entity) {
        boolean isNew = entity.getId() == null;
        LocalDateTime now = LocalDateTime.now();

        if (isNew) {
            entity.setCreadoEn(now);
        }

        if (entity.getSucursalId() == null) {
            if (entity.getSucursal() != null) {
                entity.setSucursalId(entity.getSucursal().getId());
            } else {
                entity.setSucursalId(0L);
            }
        }

        if (entity.getId() == null) {
            Long lastId = repository.findMaxId(entity.getSucursalId());
            long newId = (lastId == null ? 0L : lastId) + 1L;
            if (newId % 2 == 0) {
                newId++;
            }
            entity.setId(newId);
        }

        cerrarOtrasSesionesActivasDelDispositivo(entity, now);

        if (entity.getHoraFin() != null) {
            entity.setToken(null);
        }

        if (tieneTokenValido(entity.getToken()) && entity.getUsuario() != null) {
            repository.liberarTokenDeOtrosUsuarios(entity.getToken(), entity.getUsuario().getId());
        }

        InicioSesion e = super.save(entity);

        if (entity.getHoraFin() == null && e.getUsuario() != null && e.getId() != null
                && tieneTokenValido(e.getToken())) {
            asignarTokenExclusivo(e.getToken(), e.getUsuario().getId(), e.getId());
        }

        if (isNew) {
            publisher.publishEvent(new com.franco.dev.fmc.event.InicioSesionCreadoEvent(this, e));
        }

        return e;
    }

    private void cerrarOtrasSesionesActivasDelDispositivo(InicioSesion entity, LocalDateTime now) {
        if (entity.getIdDispositivo() == null || entity.getUsuario() == null) {
            return;
        }
        List<InicioSesion> sesionesPrevias = repository.findByUsuarioIdAndIdDispositivoAndHoraFinIsNull(
                entity.getUsuario().getId(), entity.getIdDispositivo());
        for (InicioSesion previa : sesionesPrevias) {
            if (entity.getId() != null && entity.getId().equals(previa.getId())) {
                continue;
            }
            previa.setHoraFin(now);
            previa.setToken(null);
            repository.save(previa);
        }
    }

    private boolean tieneTokenValido(String token) {
        return token != null && !token.trim().isEmpty();
    }

    @org.springframework.transaction.annotation.Transactional
    public void reclamarTokenParaUsuario(String token, Long usuarioId) {
        if (!tieneTokenValido(token) || usuarioId == null) {
            return;
        }
        repository.liberarTokenDeOtrosUsuarios(token, usuarioId);
    }

    /**
     * Reasigna el token FCM sin cerrar sesiones activas:
     * - ningún otro usuario conserva ese token
     * - el usuario no mantiene ese token en otra sesión distinta
     */
    private void asignarTokenExclusivo(String token, Long usuarioId, Long sesionActivaId) {
        repository.liberarTokenDeOtrosUsuarios(token, usuarioId);
        repository.liberarTokenDeOtrasSesiones(token, sesionActivaId);
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
        return save(entity);
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
        return deduplicarSesionesParaEnvio(repository.findByUsuarioIdInWithValidTokens(usuarioIds));
    }

    /**
     * Obtiene todas las sesiones con tokens válidos (para envío masivo de
     * notificaciones)
     * 
     * @return Lista de sesiones con tokens válidos
     */
    public List<InicioSesion> findSessionsWithValidTokens() {
        return deduplicarSesionesParaEnvio(repository.findAllWithValidTokens());
    }

    public List<InicioSesion> findActiveSessionsByTokens(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findActiveSessionsByTokens(tokens);
    }

    public InicioSesion findActiveSessionByUsuarioAndDispositivo(Long usuarioId, String idDispositivo) {
        if (usuarioId == null || idDispositivo == null || idDispositivo.trim().isEmpty()) {
            return null;
        }
        List<InicioSesion> sesiones = repository.findByUsuarioIdAndIdDispositivoAndHoraFinIsNull(usuarioId,
                idDispositivo);
        return sesiones.stream()
                .max(java.util.Comparator.comparing(InicioSesion::getId,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    /**
     * Deja un solo destino push por usuario (sesión más reciente) y deduplica
     * tokens huérfanos sin usuario asociado.
     */
    private List<InicioSesion> deduplicarSesionesParaEnvio(List<InicioSesion> sesiones) {
        Set<Long> usuariosVistos = new LinkedHashSet<>();
        Set<String> tokensHuerfanos = new LinkedHashSet<>();
        List<InicioSesion> resultado = new ArrayList<>();
        for (InicioSesion sesion : sesiones) {
            Long usuarioId = sesion.getUsuario() != null ? sesion.getUsuario().getId() : null;
            String token = sesion.getToken();
            if (usuarioId != null) {
                if (usuariosVistos.add(usuarioId)) {
                    resultado.add(sesion);
                }
            } else if (token != null && tokensHuerfanos.add(token)) {
                resultado.add(sesion);
            }
        }
        return resultado;
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