package com.franco.dev.service.vehiculos.netty;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.TelemetriaService;
import com.franco.dev.service.vehiculos.websocket.GpsTelemetriaWebSocketService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
public class GpsNettyHandler extends SimpleChannelInboundHandler<String> {

    private final GpsService gpsService;
    private final TelemetriaService telemetriaService;
    private final GpsTelemetriaWebSocketService webSocketService;

    public GpsNettyHandler(GpsService gpsService, TelemetriaService telemetriaService,
            GpsTelemetriaWebSocketService webSocketService) {
        this.gpsService = gpsService;
        this.telemetriaService = telemetriaService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Nueva conexión GPS desde: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Conexión GPS cerrada: {}", ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        log.info("Mensaje Recibido: {}", msg);

        String cleanMsg = msg.trim();
        if (cleanMsg.endsWith("#")) {
            cleanMsg = cleanMsg.substring(0, cleanMsg.length() - 1);
        }

        String[] parts = cleanMsg.split(",");

        // Validación básica de longitud H02
        if (parts.length < 3) {
            log.warn("Mensaje ignorado por longitud insuficiente: {}", cleanMsg);
            return;
        }

        String header = parts[0];
        if (!header.contains("HQ")) {
            log.debug("Mensaje no es protocolo H02: {}", header);
            return;
        }

        String imei = parts[1];
        String messageType = parts[2];

        // Buscar dispositivo registrado
        Optional<Gps> gpsOpt = gpsService.findByImei(imei);
        if (!gpsOpt.isPresent()) {
            log.warn("Dispositivo no registrado con IMEI: {}", imei);
            return;
        }

        Gps gps = gpsOpt.get();

        // Procesar según tipo de mensaje
        switch (messageType) {
            case "V1": // Ubicación normal
            case "V4": // Ubicación con alarma de corte de energía
            case "V5": // Ubicación LBS (sin GPS)
                procesarUbicacion(ctx, gps, parts, messageType, msg);
                break;
            case "BP00": // Heartbeat request
                enviarAck(ctx, imei, "BP00");
                log.debug("Heartbeat recibido de IMEI: {}", imei);
                break;
            case "BP05": // Heartbeat handshake
                enviarAck(ctx, imei, "BP05");
                break;
            default:
                log.debug("Tipo de mensaje no manejado: {} para IMEI: {}", messageType, imei);
        }
    }

    /**
     * Procesa mensajes de ubicación (V1, V4, V5)
     */
    private void procesarUbicacion(ChannelHandlerContext ctx, Gps gps, String[] parts,
            String messageType, String rawMsg) {
        try {
            // Validar longitud mínima para datos de ubicación
            if (parts.length < 12) {
                log.warn("Mensaje de ubicación incompleto para IMEI: {}", gps.getImei());
                return;
            }

            // Extracción de datos
            String timeStr = parts[3]; // HHMMSS
            String validStr = parts[4]; // A = válido, V = inválido
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedStr = parts[9];
            String dirStr = parts[10];
            String dateStr = parts[11]; // DDMMYY
            String stateStr = parts.length > 12 ? parts[12] : "";

            // Parsing Fecha/Hora
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("ddMMyy");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

            LocalDate date = LocalDate.parse(dateStr, dateFmt);
            LocalTime time = LocalTime.parse(timeStr, timeFmt);
            LocalDateTime fechaGps = LocalDateTime.of(date, time);

            // Parsing Coordenadas
            Double lat = GpsUtils.convertCoordinates(latStr, latDir);
            Double lon = GpsUtils.convertCoordinates(lonStr, lonDir);

            // Validar coordenadas (0,0 es inválido)
            boolean coordenadasValidas = lat != 0.0 && lon != 0.0;

            // Parsing Velocidad (knots a km/h si es necesario, ST-901 usa km/h)
            Double speedVal = Double.parseDouble(speedStr);
            Integer velocidad = speedVal.intValue();

            // Dirección (heading)
            Integer direccion = parseIntSafe(dirStr, 0);

            // Estado de ignición (parsear del stateStr si está disponible)
            Boolean ignicion = parseIgnicion(stateStr);

            // Determinar tipo de alarma
            String alarma = determinarAlarma(validStr, messageType, stateStr);

            // Crear entidad Telemetría
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
            telemetria.setJsonData(buildJsonData(rawMsg, stateStr, validStr, coordenadasValidas));

            // Guardar telemetría
            Telemetria saved = telemetriaService.save(telemetria);
            log.info("Telemetría guardada para IMEI: {} - Lat: {}, Lon: {}, Vel: {} km/h",
                    gps.getImei(), lat, lon, velocidad);

            // Actualizar caché de última posición en GPS
            if (coordenadasValidas) {
                gpsService.actualizarUltimaPosicion(gps.getId(), lat, lon, fechaGps, ignicion);
            }

            // Broadcast vía WebSocket a clientes frontend
            if (webSocketService != null) {
                webSocketService.broadcastTelemetria(gps, saved);
            }

            // Enviar ACK al dispositivo GPS
            enviarAck(ctx, gps.getImei(), "V1");

        } catch (Exception e) {
            log.error("Error procesando telemetría para IMEI {}: {}", gps.getImei(), e.getMessage(), e);
        }
    }

    /**
     * Envía ACK al dispositivo GPS.
     * Formato: *HQ,{imei},V1,{time}#
     */
    private void enviarAck(ChannelHandlerContext ctx, String imei, String messageType) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            String ack = String.format("*HQ,%s,%s,%s#", imei, messageType, timestamp);
            ctx.writeAndFlush(ack);
            log.debug("ACK enviado: {}", ack);
        } catch (Exception e) {
            log.error("Error enviando ACK a IMEI {}: {}", imei, e.getMessage());
        }
    }

    /**
     * Determina el tipo de alarma basado en el estado del mensaje
     */
    private String determinarAlarma(String validStr, String messageType, String stateStr) {
        if (!"A".equals(validStr)) {
            return "GPS_INVALID";
        }

        switch (messageType) {
            case "V4":
                return "POWER_CUT";
            case "V5":
                return "LBS_ONLY";
            default:
                if (stateStr != null && !stateStr.isEmpty()) {
                    // Parsear flags de estado si están disponibles
                    return parseAlarmaFromState(stateStr);
                }
                return "NORMAL";
        }
    }

    /**
     * Parsea alarmas del campo de estado
     */
    private String parseAlarmaFromState(String stateStr) {
        try {
            // El estado puede contener flags como: 00000000
            // Bit 0: ACC on
            // Bit 1: SOS
            // Bit 2: Power cut
            // etc.
            if (stateStr.length() >= 8) {
                if (stateStr.charAt(1) == '1')
                    return "SOS";
                if (stateStr.charAt(2) == '1')
                    return "POWER_CUT";
                if (stateStr.charAt(3) == '1')
                    return "OVERSPEED";
                if (stateStr.charAt(4) == '1')
                    return "GEOFENCE_EXIT";
                if (stateStr.charAt(5) == '1')
                    return "GEOFENCE_ENTER";
            }
        } catch (Exception e) {
            log.debug("Error parseando estado: {}", stateStr);
        }
        return "NORMAL";
    }

    /**
     * Parsea el estado de ignición del campo state
     */
    private Boolean parseIgnicion(String stateStr) {
        try {
            if (stateStr != null && stateStr.length() >= 1) {
                // El primer bit suele indicar ACC (ignición)
                return stateStr.charAt(0) == '1';
            }
        } catch (Exception e) {
            log.debug("Error parseando ignición: {}", stateStr);
        }
        return false;
    }

    /**
     * Construye JSON con datos adicionales para diagnóstico
     */
    private String buildJsonData(String rawMsg, String stateStr, String validStr, boolean coordenadasValidas) {
        return String.format("{\"raw\": \"%s\", \"state\": \"%s\", \"gpsValid\": \"%s\", \"coordsValid\": %s}",
                rawMsg.replace("\"", "\\\""),
                stateStr != null ? stateStr : "",
                validStr,
                coordenadasValidas);
    }

    private Integer parseIntSafe(String value, Integer defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error en canal GPS desde {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}
