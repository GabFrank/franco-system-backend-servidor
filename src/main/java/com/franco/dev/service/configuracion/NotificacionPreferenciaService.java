package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionPreferenciaUsuario;
import com.franco.dev.domain.configuracion.NotificacionTipoRole;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.configuracion.model.ConfiguracionNotificacionDto;
import com.franco.dev.repository.configuracion.NotificacionPreferenciaUsuarioRepository;
import com.franco.dev.repository.configuracion.NotificacionTipoRoleRepository;
import com.franco.dev.service.personas.RoleService;
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

    /**
     * Obtiene la configuración consolidada para un usuario.
     * Combina lo que su rol le permite ver (whitelist) con lo que él ha decidido
     * (blacklist).
     */
    public List<ConfiguracionNotificacionDto> obtenerConfiguracionesPorUsuario(Usuario usuario) {
        // 1. Obtener los roles del usuario
        List<Role> roles = roleService.findByUsuarioId(usuario.getId());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());

        // 2. Obtener tipos de notificacion permitidos para esos roles
        List<NotificacionTipoRole> tiposPermitidos = tipoRoleRepository.findByRoleIdIn(roleIds);

        // Map para eliminar duplicados si un usuario tiene multiples roles con el mismo
        // tipo
        // Se prioriza si es obligatorio en alguno de los roles
        Map<String, NotificacionTipoRole> tiposMap = new HashMap<>();
        for (NotificacionTipoRole tipo : tiposPermitidos) {
            String key = tipo.getTipoNotificacion();
            if (!tiposMap.containsKey(key) || (!tiposMap.get(key).getEsObligatorio() && tipo.getEsObligatorio())) {
                tiposMap.put(key, tipo);
            }
        }

        List<ConfiguracionNotificacionDto> resultado = new ArrayList<>();

        // 3. Cruzar con las preferencias personales del usuario
        for (NotificacionTipoRole tipoRole : tiposMap.values()) {
            boolean habilitado = true; // Por defecto habilitado

            // Buscar si el usuario tiene una preferencia guardada
            Optional<NotificacionPreferenciaUsuario> pref = preferenciaUsuarioRepository
                    .findByUsuarioIdAndTipoNotificacion(usuario.getId(), tipoRole.getTipoNotificacion());

            if (pref.isPresent()) {
                habilitado = pref.get().getHabilitado();
            }

            // Si es obligatorio por rol, forzamos habilitado (aunque usuario intente
            // hackear)
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
        // Verificar si el usuario tiene acceso a este tipo por rol
        List<Role> roles = roleService.findByUsuarioId(usuario.getId());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
        List<NotificacionTipoRole> permitidos = tipoRoleRepository.findByRoleIdIn(roleIds);

        Optional<NotificacionTipoRole> configRol = permitidos.stream()
                .filter(t -> t.getTipoNotificacion().equals(tipoNotificacion))
                .findFirst();

        if (configRol.isEmpty()) {
            // El usuario no tiene permiso para este tipo de notificacion, ignoramos
            return false;
        }

        if (configRol.get().getEsObligatorio()) {
            // Es obligatorio, no se puede deshabilitar
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

    /**
     * Verifica si un usuario debe recibir una notificacion de cierto tipo.
     */
    public boolean isNotificacionHabilitada(Long usuarioId, String tipoNotificacion) {
        // Primero verificamos si existe una preferencia explícita desactivada
        Optional<NotificacionPreferenciaUsuario> pref = preferenciaUsuarioRepository
                .findByUsuarioIdAndTipoNotificacion(usuarioId, tipoNotificacion);

        if (pref.isPresent() && !pref.get().getHabilitado()) {
            // El usuario la desactivó explícitamente.
            // Pero debemos verificar si es obligatoria por rol (safety check)
            // (Aunque esto seria costoso hacerlo en cada notificacion, asumimos la
            // preferencia es valida)
            // Para optimizar envio masivo, confiamos en la tabla de preferencia,
            // PERO la logica de actualizarPreferencia garantiza que no se guarde 'false' si
            // es obligatorio.
            return false;
        }

        // Si no hay preferencia, se asume habilitado (Opt-out strategy)
        return true;
    }
}
