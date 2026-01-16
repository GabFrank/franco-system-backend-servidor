package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionComentario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.configuracion.NotificacionComentarioRepository;
import com.franco.dev.repository.configuracion.NotificacionRepository;
import com.franco.dev.service.CrudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificacionComentarioService
        extends CrudService<NotificacionComentario, NotificacionComentarioRepository, Long> {

    @Autowired
    private NotificacionComentarioRepository repository;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private com.franco.dev.service.personas.UsuarioService usuarioService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([^\\s@\\n]+)");

    @Override
    public NotificacionComentarioRepository getRepository() {
        return repository;
    }

    @Transactional
    public NotificacionComentario crearComentario(Long notificacionId, Long usuarioId, String comentario,
            Long comentarioPadreId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));

        Usuario usuario = usuarioService.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        NotificacionComentario comentarioEntity = new NotificacionComentario();
        comentarioEntity.setNotificacion(notificacion);
        comentarioEntity.setUsuario(usuario);
        comentarioEntity.setComentario(comentario);

        if (comentarioPadreId != null) {
            NotificacionComentario padre = repository.findById(comentarioPadreId)
                    .orElse(null);
            comentarioEntity.setComentarioPadre(padre);
        }

        return repository.save(comentarioEntity);
    }

    public List<NotificacionComentario> obtenerComentariosPorNotificacion(Long notificacionId) {
        return repository.findByNotificacionId(notificacionId);
    }

    public Long contarComentariosPorNotificacion(Long notificacionId) {
        return repository.countByNotificacionId(notificacionId);
    }

    public Map<Long, Long> contarComentariosPorNotificaciones(List<Long> notificacionIds) {
        if (notificacionIds == null || notificacionIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Object[]> resultados = repository.countByNotificacionIds(notificacionIds);
        Map<Long, Long> conteos = new HashMap<>();

        for (Object[] resultado : resultados) {
            Long notificacionId = (Long) resultado[0];
            Long count = (Long) resultado[1];
            conteos.put(notificacionId, count);
        }

        for (Long notificacionId : notificacionIds) {
            conteos.putIfAbsent(notificacionId, 0L);
        }

        return conteos;
    }

    public List<String> extraerMenciones(String comentario) {
        if (comentario == null || comentario.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<String> menciones = new java.util.ArrayList<>();
        Matcher matcher = MENTION_PATTERN.matcher(comentario);

        List<Usuario> usuariosActivos = usuarioService.findAllActivos();

        while (matcher.find()) {
            int start = matcher.start();
            String textoCapturado = matcher.group(1).trim();

            if (textoCapturado.isEmpty()) {
                continue;
            }

            String textoDesdeArroba = comentario.substring(start);

            String mejorCoincidencia = null;
            int mejorLongitud = 0;

            for (Usuario usuario : usuariosActivos) {
                if (usuario.getNickname() != null
                        && usuario.getNickname().toUpperCase().startsWith(textoCapturado.toUpperCase())) {
                    String nicknameCompleto = usuario.getNickname();
                    String patronNickname = "@" + nicknameCompleto;

                    boolean coincide = textoDesdeArroba.startsWith(patronNickname + " ") ||
                            textoDesdeArroba.startsWith(patronNickname + "\n") ||
                            textoDesdeArroba.startsWith(patronNickname + "@") ||
                            textoDesdeArroba.equals(patronNickname) ||
                            (textoDesdeArroba.startsWith(patronNickname) &&
                                    textoDesdeArroba.length() == patronNickname.length());

                    if (coincide && nicknameCompleto.length() > mejorLongitud) {
                        mejorCoincidencia = nicknameCompleto;
                        mejorLongitud = nicknameCompleto.length();
                    }
                }
            }

            String nicknameFinal = mejorCoincidencia != null ? mejorCoincidencia : textoCapturado;

            if (!menciones.contains(nicknameFinal)) {
                menciones.add(nicknameFinal);
            }
        }

        return menciones;
    }

    public List<Usuario> buscarUsuariosMencionados(String comentario) {
        List<String> nicknames = extraerMenciones(comentario);
        if (nicknames == null || nicknames.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        List<Usuario> usuarios = new java.util.ArrayList<>();
        for (String nickname : nicknames) {
            usuarioService.findByNickname(nickname).ifPresent(usuarios::add);

            if (usuarios.isEmpty() || !usuarios.stream().anyMatch(u -> nickname.equals(u.getNickname()))) {
                List<Usuario> usuariosActivos = usuarioService.findAllActivos();
                for (Usuario usuario : usuariosActivos) {
                    if (usuario.getNickname() != null
                            && usuario.getNickname().toUpperCase().startsWith(nickname.toUpperCase())) {
                        List<Usuario> coincidencias = new java.util.ArrayList<>();
                        for (Usuario u : usuariosActivos) {
                            if (u.getNickname() != null
                                    && u.getNickname().toUpperCase().startsWith(nickname.toUpperCase())) {
                                coincidencias.add(u);
                            }
                        }
                        if (coincidencias.size() == 1) {
                            usuarios.add(coincidencias.get(0));
                        }
                        break;
                    }
                }
            }
        }

        List<Usuario> usuariosUnicos = new java.util.ArrayList<>();
        for (Usuario usuario : usuarios) {
            boolean existe = false;
            for (Usuario u : usuariosUnicos) {
                if (u.getId().equals(usuario.getId())) {
                    existe = true;
                    break;
                }
            }
            if (!existe) {
                usuariosUnicos.add(usuario);
            }
        }
        return usuariosUnicos;
    }
}
