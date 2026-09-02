package com.franco.dev.service.configuracion;

import com.franco.dev.domain.configuracion.NotificacionTipoEstado;
import com.franco.dev.repository.configuracion.NotificacionTipoEstadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Si un tipo de notificacion sale o no sale.
 *
 * <p>
 * Se consulta en el unico lugar por donde pasan todos los envios
 * ({@code PushNotificationService}), porque los tipos no comparten forma de
 * elegir destinatario: unos salen por rol, otros a una persona concreta y
 * otros a todas las sesiones abiertas. Un interruptor puesto en el mapa de
 * roles dejaria vivos justo a los que no pasan por ahi.
 *
 * <p>
 * ⚠️ <b>Fila ausente = activo.</b> Al agregar un tipo nuevo nadie tiene que
 * acordarse de prenderlo; lo que se apaga se apaga a proposito.
 */
@Service
public class NotificacionTipoEstadoService {

    @Autowired
    private NotificacionTipoEstadoRepository repository;

    public boolean estaActivo(String tipoNotificacion) {
        if (tipoNotificacion == null || tipoNotificacion.trim().isEmpty()) {
            return true;
        }
        Optional<NotificacionTipoEstado> estado = repository.findByTipoNotificacion(tipoNotificacion.trim());
        return !estado.isPresent() || !Boolean.FALSE.equals(estado.get().getActivo());
    }

    public List<NotificacionTipoEstado> listar() {
        return repository.findAllByOrderByTipoNotificacionAsc();
    }

    /**
     * Prende o apaga un tipo.
     *
     * <p>
     * Crea la fila si no existia, para que apagar un tipo que nunca se
     * configuro no exija una migracion.
     */
    @Transactional
    public NotificacionTipoEstado cambiar(String tipoNotificacion, Boolean activo, String motivo) {
        if (tipoNotificacion == null || tipoNotificacion.trim().isEmpty() || activo == null) {
            return null;
        }
        String tipo = tipoNotificacion.trim().toUpperCase();
        NotificacionTipoEstado estado = repository.findByTipoNotificacion(tipo)
                .orElseGet(() -> {
                    NotificacionTipoEstado nuevo = new NotificacionTipoEstado();
                    nuevo.setTipoNotificacion(tipo);
                    return nuevo;
                });
        estado.setActivo(activo);
        estado.setMotivo(motivo);
        return repository.save(estado);
    }
}
