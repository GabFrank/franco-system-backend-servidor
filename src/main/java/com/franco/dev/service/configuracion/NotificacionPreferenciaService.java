package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionPreferenciaUsuario;
import com.franco.dev.domain.configuracion.NotificacionTipoRole;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.configuracion.model.ConfiguracionNotificacionDto;
import com.franco.dev.repository.configuracion.NotificacionPreferenciaUsuarioRepository;
import com.franco.dev.repository.configuracion.NotificacionTipoRoleRepository;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.domain.personas.UsuarioRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificacionPreferenciaService {

    @Autowired
    private NotificacionTipoRoleRepository tipoRoleRepository;

    @Autowired
    private NotificacionPreferenciaUsuarioRepository preferenciaUsuarioRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    public List<ConfiguracionNotificacionDto> obtenerConfiguracionesPorUsuario(Usuario usuario) {
        List<Role> roles = roleService.findByUsuarioId(usuario.getId());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());

        List<NotificacionTipoRole> tiposPermitidos = tipoRoleRepository.findByRoleIdIn(roleIds);

        Map<String, NotificacionTipoRole> tiposMap = new HashMap<>();
        for (NotificacionTipoRole tipo : tiposPermitidos) {
            String key = tipo.getTipoNotificacion();
            if (!tiposMap.containsKey(key) || (!tiposMap.get(key).getEsObligatorio() && tipo.getEsObligatorio())) {
                tiposMap.put(key, tipo);
            }
        }

        List<ConfiguracionNotificacionDto> resultado = new ArrayList<>();

        for (NotificacionTipoRole tipoRole : tiposMap.values()) {
            boolean habilitado = true;

            Optional<NotificacionPreferenciaUsuario> pref = preferenciaUsuarioRepository
                    .findByUsuarioIdAndTipoNotificacion(usuario.getId(), tipoRole.getTipoNotificacion());

            if (pref.isPresent()) {
                habilitado = pref.get().getHabilitado();
            }
            if (tipoRole.getEsObligatorio()) {
                habilitado = true;
            }

            resultado.add(new ConfiguracionNotificacionDto(
                    tipoRole.getTipoNotificacion(),
                    tipoRole.getDescripcion(),
                    habilitado,
                    tipoRole.getEsObligatorio()));
        }

        return resultado;
    }

    @Transactional
    public boolean actualizarPreferencia(Usuario usuario, String tipoNotificacion, Boolean habilitado) {
        List<Role> roles = roleService.findByUsuarioId(usuario.getId());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
        List<NotificacionTipoRole> permitidos = tipoRoleRepository.findByRoleIdIn(roleIds);

        Optional<NotificacionTipoRole> configRol = permitidos.stream()
                .filter(t -> t.getTipoNotificacion().equals(tipoNotificacion))
                .findFirst();

        if (configRol.isEmpty()) {
            return false;
        }

        if (configRol.get().getEsObligatorio()) {
            return false;
        }

        Optional<NotificacionPreferenciaUsuario> existente = preferenciaUsuarioRepository
                .findByUsuarioIdAndTipoNotificacion(usuario.getId(), tipoNotificacion);

        if (existente.isPresent()) {
            existente.get().setHabilitado(habilitado);
            preferenciaUsuarioRepository.save(existente.get());
        } else {
            NotificacionPreferenciaUsuario nueva = new NotificacionPreferenciaUsuario();
            nueva.setUsuario(usuario);
            nueva.setTipoNotificacion(tipoNotificacion);
            nueva.setHabilitado(habilitado);
            preferenciaUsuarioRepository.save(nueva);
        }

        return true;
    }

    public boolean isNotificacionHabilitada(Long usuarioId, String tipoNotificacion) {
        Optional<NotificacionPreferenciaUsuario> pref = preferenciaUsuarioRepository
                .findByUsuarioIdAndTipoNotificacion(usuarioId, tipoNotificacion);

        if (pref.isPresent() && !pref.get().getHabilitado()) {
            return false;
        }
        return true;
    }

    public List<Usuario> obtenerUsuariosPorTipoNotificacion(String tipoNotificacion) {
        List<NotificacionTipoRole> configs = tipoRoleRepository.findByTipoNotificacion(tipoNotificacion);

        Set<Usuario> usuariosSet = new HashSet<>();

        for (NotificacionTipoRole config : configs) {
            List<UsuarioRole> usuarioRoles = usuarioRoleService.findByRoleId(config.getRole().getId());
            for (UsuarioRole ur : usuarioRoles) {
                if (ur.getUsuario() != null) {
                    usuariosSet.add(ur.getUsuario());
                }
            }
        }
        return new ArrayList<>(usuariosSet);
    }
}
