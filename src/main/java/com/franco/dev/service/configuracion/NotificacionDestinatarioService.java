package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.NotificacionDestinatarioRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.personas.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificacionDestinatarioService
        extends CrudService<NotificacionDestinatario, NotificacionDestinatarioRepository, Long> {

    @Autowired
    private NotificacionDestinatarioRepository repository;

    @Autowired
    private UsuarioService usuarioService;

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
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Pageable pageable) {
        return repository.findAll(getSpecification(usuarioId, estadoTablero, leida, fechaInicio, fechaFin), pageable);
    }

    private Specification<NotificacionDestinatario> getSpecification(
            Long usuarioId,
            EstadoNotificacionTablero estadoTablero,
            Boolean leida,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Join<NotificacionDestinatario, Notificacion> notificacionJoin = root.join("notificacion", JoinType.INNER);

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("notificacion", JoinType.INNER);
                query.orderBy(cb.desc(notificacionJoin.get("creadoEn")));
            }

            predicates.add(cb.equal(root.get("usuario").get("id"), usuarioId));

            if (estadoTablero != null) {
                predicates.add(cb.equal(notificacionJoin.get("estadoTablero"), estadoTablero));
            }

            if (leida != null) {
                predicates.add(cb.equal(root.get("leida"), leida));
            }

            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(notificacionJoin.get("creadoEn"), fechaInicio));
            }

            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(notificacionJoin.get("creadoEn"), fechaFin));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public Long countByUsuarioIdAndFilters(
            Long usuarioId,
            EstadoNotificacionTablero estadoTablero,
            Boolean leida,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin) {
        return repository.count(getSpecification(usuarioId, estadoTablero, leida, fechaInicio, fechaFin));
    }

    public Long countNoLeidasByUsuarioId(Long usuarioId) {
        return repository.countNoLeidasByUsuarioId(usuarioId);
    }

    @Transactional
    public void crearDestinatarios(Notificacion notificacion, List<Usuario> usuarios) {
        usuarios.forEach(usuario -> crearDestinatario(notificacion, usuario));
    }

    public List<Usuario> obtenerUsuariosDestinatarios(Long notificacionId) {
        List<NotificacionDestinatario> destinatarios = repository
                .findNotificacionesDestinatariosByNotificacionId(notificacionId);
        return destinatarios.stream()
                .map(NotificacionDestinatario::getUsuario)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Usuario> obtenerUsuariosConAcceso(Long notificacionId) {
        List<Long> usuarioIds = repository.findUsuarioIdsConAccesoNotificacion(notificacionId);

        List<Usuario> usuarios = usuarioIds.stream()
                .map(id -> usuarioService.findById(id))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toList());

        usuarios.sort((u1, u2) -> {
            String nombre1 = u1.getPersona() != null && u1.getPersona().getNombre() != null
                    ? u1.getPersona().getNombre()
                    : u1.getNickname() != null ? u1.getNickname() : "";
            String nombre2 = u2.getPersona() != null && u2.getPersona().getNombre() != null
                    ? u2.getPersona().getNombre()
                    : u2.getNickname() != null ? u2.getNickname() : "";
            int comparacion = nombre1.compareToIgnoreCase(nombre2);
            if (comparacion != 0) {
                return comparacion;
            }
            String nick1 = u1.getNickname() != null ? u1.getNickname() : "";
            String nick2 = u2.getNickname() != null ? u2.getNickname() : "";
            return nick1.compareToIgnoreCase(nick2);
        });

        return usuarios;
    }
}
