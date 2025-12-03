package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.NotificacionRepository;
import com.franco.dev.service.CrudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificacionService extends CrudService<Notificacion, NotificacionRepository, Long> {

    @Autowired
    private NotificacionRepository repository;

    @Override
    public NotificacionRepository getRepository() {
        return repository;
    }

    @Transactional
    public boolean cambiarEstadoTablero(Long notificacionId, EstadoNotificacionTablero nuevoEstado, Long usuarioId) {
        return repository.findById(notificacionId)
            .map(n -> {
                n.setEstadoTablero(nuevoEstado);
                if (nuevoEstado == EstadoNotificacionTablero.VERIFICADO) {
                    Usuario usuario = new Usuario();
                    usuario.setId(usuarioId);
                    n.setVerificadoPorUsuario(usuario);
                    n.setFechaVerificacion(LocalDateTime.now());
                }
                repository.save(n);
                return true;
            }).orElse(false);
    }

    @Transactional
    public boolean cambiarEstadoTablero(Long notificacionId, EstadoNotificacionTablero nuevoEstado, Usuario usuario) {
        return repository.findById(notificacionId)
            .map(n -> {
                n.setEstadoTablero(nuevoEstado);
                if (nuevoEstado == EstadoNotificacionTablero.VERIFICADO && usuario != null) {
                    n.setVerificadoPorUsuario(usuario);
                    n.setFechaVerificacion(LocalDateTime.now());
                }
                repository.save(n);
                return true;
            }).orElse(false);
    }
}

