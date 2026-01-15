package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.Necesidad;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.dto.VencimientoProductoDto;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.repository.operaciones.NecesidadRepository;
import com.franco.dev.repository.operaciones.TransferenciaRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class TransferenciaService extends CrudService<Transferencia, TransferenciaRepository, Long> {
    private final TransferenciaRepository repository;
    private final com.franco.dev.fmc.service.NotificationTemplateService notificationTemplateService;
    private final com.franco.dev.fmc.service.PushNotificationService pushNotificationService;
    private final com.franco.dev.fmc.service.NotificationRoleService notificationRoleService;

    @Override
    public TransferenciaRepository getRepository() {
        return repository;
    }

    public List<Transferencia> findBySucursalOrigenId(Long id) {
        return repository.findBySucursalOrigenId(id);
    }

    public List<Transferencia> findBySucursalDestinoId(Long id) {
        return repository.findBySucursalDestinoId(id);
    }

    public List<Transferencia> findByDate(String start, String end) {
        return repository.findByDate(start, end);
    }

    public List<Transferencia> findByUsuario(Long id) {
        return repository.findByUsuario(id);
    }

    public Page<Transferencia> findByFilter(Long sucursalOrigenId, Long sucursalDestinoId, TransferenciaEstado estado,
            TipoTransferencia tipo, EtapaTransferencia etapa, Boolean isOrigen, Boolean isDestino,
            LocalDateTime creadoDesde,
            LocalDateTime creadoHasta, Pageable pageable) {
        return repository.findByFilter(sucursalOrigenId, sucursalDestinoId, estado, tipo, etapa, isOrigen, isDestino,
                creadoDesde, creadoHasta, pageable);
    }

    public List<VencimientoProductoDto> findProductosVencidos(Long sucId, LocalDateTime fechaInicio,
            LocalDateTime fechaFin) {
        return repository.findProductoVencido(sucId, fechaInicio, fechaFin);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Transferencia save(Transferencia entity) {
        if (entity.getId() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getEtapa() == null)
            entity.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_CREACION);

        // Lógica de notificaciones para cambio de sucursal
        if (entity.getId() != null) {
            Transferencia oldTransferencia = repository.findById(entity.getId()).orElse(null);
            if (oldTransferencia != null) {
                checkAndSendNotification(oldTransferencia, entity);
            }
        }

        Transferencia e = super.save(entity);
        // personaPublisher.publish(p);
        return e;
    }

    private void checkAndSendNotification(Transferencia oldTransferencia, Transferencia newTransferencia) {
        try {
            // Verificar cambio en Sucursal Origen
            if (oldTransferencia.getSucursalOrigen() != null && newTransferencia.getSucursalOrigen() != null
                    && !oldTransferencia.getSucursalOrigen().getId()
                            .equals(newTransferencia.getSucursalOrigen().getId())) {
                sendCambioSucursalNotification(newTransferencia, oldTransferencia.getSucursalOrigen(),
                        newTransferencia.getSucursalOrigen(), true);
            }

            // Verificar cambio en Sucursal Destino
            if (oldTransferencia.getSucursalDestino() != null && newTransferencia.getSucursalDestino() != null
                    && !oldTransferencia.getSucursalDestino().getId()
                            .equals(newTransferencia.getSucursalDestino().getId())) {
                sendCambioSucursalNotification(newTransferencia, oldTransferencia.getSucursalDestino(),
                        newTransferencia.getSucursalDestino(), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendCambioSucursalNotification(Transferencia transferencia,
            com.franco.dev.domain.empresarial.Sucursal oldSucursal,
            com.franco.dev.domain.empresarial.Sucursal newSucursal, boolean isOrigen) {
        List<String> roles = notificationRoleService.getRolesForCambioSucursalPreTransferencia();
        List<Long> userIds = notificationRoleService.getUserIdsByRoles(roles);

        if (userIds.isEmpty())
            return;

        com.franco.dev.fmc.model.PushNotificationRequest request = notificationTemplateService
                .cambioSucursalPreTransferencia(
                        transferencia,
                        oldSucursal,
                        newSucursal,
                        isOrigen);

        if (request != null) {
            request.setUsuarioIds(userIds);
            pushNotificationService.sendPushNotificationToToken(request);
        }
    }
}