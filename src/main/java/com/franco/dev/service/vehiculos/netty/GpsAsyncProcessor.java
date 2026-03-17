package com.franco.dev.service.vehiculos.netty;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.TelemetriaService;
import com.franco.dev.service.vehiculos.websocket.GpsTelemetriaWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Procesador asíncrono para mensajes GPS.
 * Desacopla la lógica de persistencia y notificaciones de los hilos de Netty.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GpsAsyncProcessor {

    private final GpsService gpsService;
    private final TelemetriaService telemetriaService;
    private final GpsTelemetriaWebSocketService webSocketService;
    private final PushNotificationService pushNotificationService;

    @Async
    public void processMessage(String imei, String messageType, String[] parts, String rawMsg) {
        try {
            // Buscar dispositivo registrado
            Optional<Gps> gpsOpt = gpsService.findByImeiWithVehiculo(imei);
            if (!gpsOpt.isPresent()) {
                log.warn("Dispositivo no registrado con IMEI: {}", imei);
                return;
            }

            Gps gps = gpsOpt.get();

            switch (messageType) {
                case "V1":
                case "V4":
                case "V5":
                    procesarUbicacion(gps, parts, messageType, rawMsg);
                    break;
                case "S20":
                    procesarRespuestaConfiguracion(gps, rawMsg);
                    break;
                default:
                    log.debug("Mensaje de tipo {} para IMEI {} no requiere procesamiento asíncrono adicional", messageType, imei);
            }
        } catch (Exception e) {
            log.error("Error en procesamiento asíncrono para IMEI {}: {}", imei, e.getMessage(), e);
        }
    }

    private void procesarUbicacion(Gps gps, String[] parts, String messageType, String rawMsg) {
        try {
            if (parts.length < 12) {
                log.warn("Mensaje de ubicación incompleto para IMEI: {}", gps.getImei());
                return;
            }

            // Extracción y Parsing (HHMMSS, A/V, Lat, Lon, Vel, Dir, DDMMYY)
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("ddMMyy");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

            LocalDate date = LocalDate.parse(parts[11], dateFmt);
            LocalTime time = LocalTime.parse(parts[3], timeFmt);
            LocalDateTime fechaGps = LocalDateTime.of(date, time);

            Double lat = GpsUtils.convertCoordinates(parts[5], parts[6]);
            Double lon = GpsUtils.convertCoordinates(parts[7], parts[8]);
            boolean coordenadasValidas = lat != 0.0 && lon != 0.0;

            Integer velocidad = Double.valueOf(parts[9]).intValue();
            Integer direccion = parseIntSafe(parts[10], 0);
            String stateStr = parts.length > 12 ? parts[12] : "";
            Boolean ignicion = parseIgnicion(stateStr);
            String alarma = determinarAlarma(parts[4], messageType, stateStr);

            // Crear y Guardar Telemetría
            Telemetria telemetria = new Telemetria();
            telemetria.setDispositivo(gps);
            telemetria.setFechaGps(fechaGps);
            telemetria.setFechaServidor(LocalDateTime.now());
            telemetria.setLatitud(lat);
            telemetria.setLongitud(lon);
            telemetria.setVelocidad(velocidad);
            telemetria.setDireccion(direccion);
            telemetria.setIgnicion(ignicion);
            telemetria.setAlarma(alarma);
            telemetria.setJsonData(buildJsonData(rawMsg, stateStr, parts[4], coordenadasValidas));

            Telemetria saved = telemetriaService.save(telemetria);
            log.info("Telemetría guardada asíncronamente para IMEI: {} - Vel: {} km/h", gps.getImei(), velocidad);

            // Procesar alertas PUSH
            procesarAlertasPush(gps, saved, alarma);

            // Actualizar caché de última posición
            if (coordenadasValidas) {
                gpsService.actualizarUltimaPosicion(gps.getId(), lat, lon, fechaGps, ignicion);
            }

            // Broadcast vía WebSocket
            if (webSocketService != null) {
                webSocketService.broadcastTelemetria(gps, saved);
            }

        } catch (Exception e) {
            log.error("Error procesando ubicación asíncrona para IMEI {}: {}", gps.getImei(), e.getMessage());
        }
    }

    private void procesarRespuestaConfiguracion(Gps gps, String rawMsg) {
        try {
            String content = rawMsg.toUpperCase();
            boolean isOk = content.contains("OK") || content.contains("SUCCESS") || content.contains("SET OK");

            if (isOk) {
                if (content.contains("940")) {
                    gpsService.updateMotorBloqueado(gps.getId(), true);
                } else if (content.contains("941")) {
                    gpsService.updateMotorBloqueado(gps.getId(), false);
                } else if (content.contains("SLEEP")) {
                    boolean enabled = content.contains(" 1") || content.contains(" 5") || content.contains("ON");
                    gpsService.updateModoSueno(gps.getId(), enabled);
                }
                log.info("Respuesta de configuración procesada para IMEI {}", gps.getImei());
            }
        } catch (Exception e) {
            log.error("Error procesando respuesta config para IMEI {}: {}", gps.getImei(), e.getMessage());
        }
    }

    private void procesarAlertasPush(Gps gps, Telemetria telemetria, String alarma) {
        if (pushNotificationService == null) return;

        String vehiculoDesc = gps.getVehiculo() != null && gps.getVehiculo().getModelo() != null
                ? gps.getVehiculo().getModelo().getDescripcion()
                : "Vehículo";

        // Alerta Velocidad
        if (Boolean.TRUE.equals(gps.getAlertaVelocidad())) {
            Integer limite = gps.getVelocidadLimite() != null ? gps.getVelocidadLimite() : 100;
            if (telemetria.getVelocidad() > limite) {
                sendPush(gps, "GPS_EXCESO_VELOCIDAD", "Alerta de velocidad (" + vehiculoDesc + ")",
                        "Velocidad: " + telemetria.getVelocidad() + " km/h (límite: " + limite + ")");
            }
        }

        // Alerta Vibración
        if (Boolean.TRUE.equals(gps.getAlertaVibracion()) && "SHOCK".equalsIgnoreCase(alarma)) {
            sendPush(gps, "GPS_SHOCK", "Alerta de vibración (" + vehiculoDesc + ")", "Se detectó movimiento/choque");
        }

        // Alerta Ignición
        Boolean ignicionActual = telemetria.getIgnicion();
        Boolean ignicionAnterior = gps.getUltimaIgnicion();
        if (Boolean.TRUE.equals(gps.getAlertaAcc()) && ignicionActual != null && ignicionAnterior != null && !ignicionActual.equals(ignicionAnterior)) {
            String estado = ignicionActual ? "ENCENDIDO" : "APAGADO";
            sendPush(gps, ignicionActual ? "GPS_IGNICION_ON" : "GPS_IGNICION_OFF", 
                    "Cambio de ignición (" + vehiculoDesc + ")", "El vehículo se ha " + estado.toLowerCase());
        }
    }

    private void sendPush(Gps gps, String tipo, String titulo, String mensaje) {
        String data = String.format("{\"imei\":\"%s\",\"tipo\":\"%s\"}", gps.getImei(), tipo);
        pushNotificationService.enviarAlertaTipo(tipo, titulo, mensaje, data);
    }

    private String determinarAlarma(String validStr, String messageType, String stateStr) {
        if (!"A".equals(validStr)) return "GPS_INVALID";
        if ("V4".equals(messageType)) return "POWER_CUT";
        if ("V5".equals(messageType)) return "LBS_ONLY";
        return parseAlarmaFromState(stateStr);
    }

    private String parseAlarmaFromState(String stateStr) {
        if (stateStr == null || stateStr.isEmpty()) return "NORMAL";
        try {
            long state = Long.parseLong(stateStr, 16);
            if ((state & 0x02) != 0) return "SOS";
            if ((state & 0x04) != 0) return "POWER_CUT";
            if ((state & 0x08) != 0) return "SHOCK";
            if ((state & 0x10) != 0) return "LOW_BATTERY";
        } catch (Exception ignored) {}
        return "NORMAL";
    }

    private Boolean parseIgnicion(String stateStr) {
        if (stateStr == null || stateStr.isEmpty()) return false;
        try {
            long state = Long.parseLong(stateStr, 16);
            return (state & 0x01) != 0;
        } catch (Exception e) { return false; }
    }

    private String buildJsonData(String rawMsg, String stateStr, String validStr, boolean coordenadasValidas) {
        return String.format("{\"raw\": \"%s\", \"state\": \"%s\", \"gpsValid\": \"%s\", \"coordsValid\": %s}",
                rawMsg.replace("\"", "\\\""), stateStr != null ? stateStr : "", validStr, coordenadasValidas);
    }

    private Integer parseIntSafe(String value, Integer defaultValue) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }
}
