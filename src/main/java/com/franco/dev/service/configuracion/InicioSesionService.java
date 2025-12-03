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

    @Override
    public InicioSesion save(InicioSesion entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        InicioSesion e = super.save(entity);
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

        System.out.println("[DEBUG] ===== INICIO detectarYNotificarNuevoDispositivo =====");
        
        if (sesion == null) {
            System.out.println("[DEBUG] Sesión es null, retornando...");
            return;
        }
        
        if (sesion.getUsuario() == null) {
            System.out.println("[DEBUG] Usuario es null, retornando...");
            return;
        }
        
        System.out.println("[DEBUG] Usuario ID: " + sesion.getUsuario().getId());
        System.out.println("[DEBUG] Usuario Nombre: " + 
                (sesion.getUsuario().getPersona() != null ? sesion.getUsuario().getPersona().getNombre() : "Sin persona"));
        System.out.println("[DEBUG] Usuario Nickname: " + sesion.getUsuario().getNickname());

        try {
            boolean esDispositivoNuevo = sesion.getIdDispositivo() != null && 
                    !repository.existsByUsuarioIdAndIdDispositivo(
                            sesion.getUsuario().getId(), sesion.getIdDispositivo());

            System.out.println("[DEBUG] Es dispositivo nuevo: " + esDispositivoNuevo);

            String nombreSucursal = null;
            if (sesion.getSucursal() != null && sucursalService != null) {
                com.franco.dev.domain.empresarial.Sucursal sucursal = sucursalService
                        .findById(sesion.getSucursal().getId()).orElse(null);
                if (sucursal != null) {
                    nombreSucursal = sucursal.getNombre();
                }
            }
            if (esDispositivoNuevo) {
                System.out.println("[DEBUG] Enviando notificación de dispositivo nuevo...");
                String tipoDispositivo = sesion.getTipoDespositivo() != null
                        ? sesion.getTipoDespositivo().toString()
                        : "DESCONOCIDO";

                com.franco.dev.fmc.model.PushNotificationRequest requestSeguridad = notificationTemplateService
                        .nuevoDispositivoDetectado(tipoDispositivo, nombreSucursal);

                requestSeguridad.setUsuarioIds(java.util.Collections.singletonList(sesion.getUsuario().getId()));
                pushNotificationService.sendPushNotificationToToken(requestSeguridad);
                System.out.println("[DEBUG] Notificación de dispositivo nuevo enviada");
            }
            System.out.println("[DEBUG] Preparando notificación de bienvenida...");
            String nombreUsuario = sesion.getUsuario().getPersona() != null && sesion.getUsuario().getPersona().getNombre() != null
                    ? sesion.getUsuario().getPersona().getNombre() 
                    : sesion.getUsuario().getNickname();
            
            System.out.println("[DEBUG] Nombre usuario para notificación: " + nombreUsuario);
            
            com.franco.dev.fmc.model.PushNotificationRequest requestBienvenida = 
                    new com.franco.dev.fmc.model.PushNotificationRequest();
            requestBienvenida.setTitle("SE HA INICIADO SESION EN SU CUENTA");
            requestBienvenida.setMessage("BIENVENIDO " + (nombreUsuario != null ? nombreUsuario.toUpperCase() : "USUARIO"));
            requestBienvenida.setData("/");
            requestBienvenida.setType("LOGIN");
            requestBienvenida.setUsuarioIds(java.util.Collections.singletonList(sesion.getUsuario().getId()));
            
            System.out.println("[DEBUG] Enviando notificación de bienvenida al usuario ID: " + sesion.getUsuario().getId());
            pushNotificationService.sendPushNotificationToToken(requestBienvenida);
            System.out.println("[DEBUG] Notificación de bienvenida enviada exitosamente");

        } catch (Exception e) {
            System.err.println("[ERROR] Error al detectar/notificar inicio de sesión: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[DEBUG] ===== FIN detectarYNotificarNuevoDispositivo =====");
    }
}