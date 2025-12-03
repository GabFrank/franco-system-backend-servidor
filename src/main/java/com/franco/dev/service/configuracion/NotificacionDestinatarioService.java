package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.NotificacionDestinatarioRepository;
import com.franco.dev.service.CrudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionDestinatarioService extends CrudService<NotificacionDestinatario, NotificacionDestinatarioRepository, Long> {

    @Autowired
    private NotificacionDestinatarioRepository repository;

    @Override
    public NotificacionDestinatarioRepository getRepository() {
        return repository;
    }

    @Transactional
    public NotificacionDestinatario crearDestinatario(Notificacion notificacion, Usuario usuario) {
        NotificacionDestinatario destinatario = new NotificacionDestinatario();
        destinatario.setNotificacion(notificacion);
        destinatario.setUsuario(usuario);
        destinatario.setLeida(false);
        return repository.save(destinatario);
    }

    @Transactional
    public boolean marcarComoLeida(Long notificacionId, Long usuarioId) {
        return repository.findByNotificacionIdAndUsuarioId(notificacionId, usuarioId)
            .map(nd -> {
                nd.setLeida(true);
                nd.setFechaLeida(LocalDateTime.now());
                repository.save(nd);
                return true;
            }).orElse(false);
    }

    public Page<NotificacionDestinatario> findByUsuarioId(Long usuarioId, Boolean leida, Pageable pageable) {
        return repository.findByUsuarioId(usuarioId, leida, pageable);
    }

    public Page<NotificacionDestinatario> findByUsuarioIdAndFilters(
            Long usuarioId, 
            EstadoNotificacionTablero estadoTablero,
            Boolean leida,
            Pageable pageable) {
        String estadoStr = estadoTablero != null ? estadoTablero.name() : null;
        return repository.findByUsuarioIdAndFilters(usuarioId, estadoStr, leida, pageable);
    }

    public Long countByUsuarioIdAndFilters(
            Long usuarioId,
            EstadoNotificacionTablero estadoTablero,
            Boolean leida) {
        String estadoStr = estadoTablero != null ? estadoTablero.name() : null;
        return repository.countByUsuarioIdAndFilters(usuarioId, estadoStr, leida);
    }

    public Long countNoLeidasByUsuarioId(Long usuarioId) {
        return repository.countNoLeidasByUsuarioId(usuarioId);
    }

    @Transactional
    public void crearDestinatarios(Notificacion notificacion, List<Usuario> usuarios) {
        usuarios.forEach(usuario -> crearDestinatario(notificacion, usuario));
    }
}

