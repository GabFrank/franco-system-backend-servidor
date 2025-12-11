package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.repository.configuracion.NotificacionUsuarioRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionUsuarioService {

    @Autowired
    private NotificacionUsuarioRepository notificacionUsuarioRepository;

    @Transactional
    public boolean marcarComoLeida(Long notificacionUsuarioId) {
        return notificacionUsuarioRepository.findById(notificacionUsuarioId)
                .map(nu -> {
                    nu.setLeida(Boolean.TRUE);
                    nu.setFechaLeida(LocalDateTime.now());
                    notificacionUsuarioRepository.save(nu);
                    return true;
                }).orElse(Boolean.FALSE);
    }

    @Transactional
    public boolean actualizarEstadoTablero(Long notificacionUsuarioId, EstadoNotificacionTablero estado) {
        return notificacionUsuarioRepository.findById(notificacionUsuarioId)
                .map(nu -> {
                    nu.setEstadoTablero(estado);
                    notificacionUsuarioRepository.save(nu);
                    return true;
                }).orElse(Boolean.FALSE);
    }

    @Transactional
    public boolean registrarInteraccion(Long notificacionUsuarioId, String accion) {
        return notificacionUsuarioRepository.findById(notificacionUsuarioId)
                .map(nu -> {
                    nu.setInteractuada(Boolean.TRUE);
                    nu.setFechaInteraccion(LocalDateTime.now());
                    nu.setAccionRealizada(accion);
                    notificacionUsuarioRepository.save(nu);
                    return true;
                }).orElse(Boolean.FALSE);
    }

    public Page<NotificacionUsuario> findByUsuarioId(Long usuarioId, String tokenFcm, Pageable pageable) {
        if (tokenFcm != null && !tokenFcm.isEmpty()) {
            return notificacionUsuarioRepository.findAllByUsuarioIdAndTokenFcm(usuarioId, tokenFcm, pageable);
        }
        return notificacionUsuarioRepository.findAllByUsuarioId(usuarioId, pageable);
    }

    public Page<NotificacionUsuario> findByUsuarioIdAndEstadoTablero(Long usuarioId, String tokenFcm,
            EstadoNotificacionTablero estado, Pageable pageable) {
        return notificacionUsuarioRepository.findByUsuarioIdAndTokenFcmAndEstadoTablero(
                usuarioId,
                tokenFcm,
                estado,
                pageable);
    }

    public Page<NotificacionUsuario> findByFilters(Long usuarioId, String tokenFcm, EstadoNotificacionTablero estado,
            Boolean leida, Pageable pageable) {
        return notificacionUsuarioRepository.findByFilters(
                usuarioId,
                tokenFcm,
                estado,
                leida,
                pageable);
    }

    public Long countByFilters(Long usuarioId, String tokenFcm, EstadoNotificacionTablero estado, Boolean leida) {
        return notificacionUsuarioRepository.countByFilters(
                usuarioId,
                tokenFcm,
                estado,
                leida);
    }
}
