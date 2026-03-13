package com.franco.dev.service.vehiculos.websocket;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.service.vehiculos.dto.TelemetriaDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class GpsTelemetriaWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Async
    public void broadcastTelemetria(Gps gps, Telemetria telemetria) {
        try {
            TelemetriaDTO dto = convertToDTO(gps, telemetria);

            messagingTemplate.convertAndSend("/topic/gps/all", dto);

            messagingTemplate.convertAndSend("/topic/gps/" + gps.getId(), dto);

            if (gps.getVehiculo() != null && gps.getVehiculo().getId() != null) {
                messagingTemplate.convertAndSend("/topic/gps/vehiculo/" + gps.getVehiculo().getId(), dto);
            }

            log.debug("Telemetría broadcasted para IMEI: {}", gps.getImei());

        } catch (Exception e) {
            log.error("Error broadcasting telemetría via WebSocket", e);
        }
    }

    @Async
    public void broadcastEstadoDispositivo(Long gpsId, String imei, boolean conectado) {
        try {
            messagingTemplate.convertAndSend("/topic/gps/status",
                    new EstadoDispositivoDTO(gpsId, imei, conectado));
        } catch (Exception e) {
            log.error("Error broadcasting estado de dispositivo", e);
        }
    }

    @Async
    public void broadcastGpsUpdate(Gps gps) {
        try {
            messagingTemplate.convertAndSend("/topic/gps/update", gps.getId());
        } catch (Exception e) {
            log.error("Error broadcasting gps update", e);
        }
    }

    private TelemetriaDTO convertToDTO(Gps gps, Telemetria telemetria) {
        TelemetriaDTO.TelemetriaDTOBuilder builder = TelemetriaDTO.builder()
                .gpsId(gps.getId())
                .imei(gps.getImei())
                .modeloTracker(gps.getModeloTracker())
                .activo(gps.getActivo())
                .latitud(telemetria.getLatitud())
                .longitud(telemetria.getLongitud())
                .velocidad(telemetria.getVelocidad())
                .direccion(telemetria.getDireccion())
                .ignicion(telemetria.getIgnicion())
                .alarma(telemetria.getAlarma())
                .fechaGps(telemetria.getFechaGps())
                .fechaServidor(telemetria.getFechaServidor());

        if (gps.getVehiculo() != null) {
            builder.vehiculoId(gps.getVehiculo().getId())
                    .vehiculoChapa(gps.getVehiculo().getChapa());

            if (gps.getVehiculo().getModelo() != null) {
                builder.vehiculoModelo(gps.getVehiculo().getModelo().getDescripcion());

                if (gps.getVehiculo().getModelo().getMarca() != null) {
                    builder.vehiculoMarca(gps.getVehiculo().getModelo().getMarca().getDescripcion());
                }
            }
        }

        return builder.build();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class EstadoDispositivoDTO {
        private Long gpsId;
        private String imei;
        private boolean conectado;
    }
}
