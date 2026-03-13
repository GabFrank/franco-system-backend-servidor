package com.franco.dev.service.vehiculos.netty;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.TelemetriaService;
import com.franco.dev.service.vehiculos.websocket.GpsTelemetriaWebSocketService;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
public class GpsNettyHandler extends SimpleChannelInboundHandler<Object> {

    private final GpsService gpsService;
    private final TelemetriaService telemetriaService;
    private final GpsTelemetriaWebSocketService webSocketService;
    private final GpsConnectionManager connectionManager;
    private final com.franco.dev.fmc.service.PushNotificationService pushNotificationService;
    private String currentImei;

    public GpsNettyHandler(GpsService gpsService, TelemetriaService telemetriaService,
            GpsTelemetriaWebSocketService webSocketService, GpsConnectionManager connectionManager,
            com.franco.dev.fmc.service.PushNotificationService pushNotificationService) {
        this.gpsService = gpsService;
        this.telemetriaService = telemetriaService;
        this.webSocketService = webSocketService;
        this.connectionManager = connectionManager;
        this.pushNotificationService = pushNotificationService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("Nueva conexión GPS desde: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Conexión GPS cerrada: {}", ctx.channel().remoteAddress());
        if (currentImei != null) {
            connectionManager.unregister(currentImei);
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof String) {
            handleAscii(ctx, (String) msg);
            return;
        }

        if (msg instanceof byte[]) {
            handleBinary(ctx, (byte[]) msg);
            return;
        }

        log.warn("Mensaje GPS de tipo no soportado {} desde {}", msg != null ? msg.getClass() : "null",
                ctx.channel().remoteAddress());
    }

    private void handleBinary(ChannelHandlerContext ctx, byte[] bytes) {
        // Log en HEX para poder implementar el parse real con evidencia.
        String hex = ByteBufUtil.hexDump(bytes);
        log.info("Frame binario recibido ({} bytes) desde {}: {}",
                bytes.length, ctx.channel().remoteAddress(), hex);

        // A veces dentro del frame binario viene un *HQ...# embebido; intentamos
        // extraerlo.
        String asciiLoose = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        int start = asciiLoose.indexOf("*HQ");
        if (start >= 0) {
            int end = asciiLoose.indexOf('#', start);
            if (end > start) {
                String extracted = asciiLoose.substring(start, end + 1);
                log.info("Extraído mensaje *HQ desde frame binario: {}", extracted);
                try {
                    handleAscii(ctx, extracted);
                } catch (Exception e) {
                    log.error("Error procesando *HQ extraído desde binario: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleAscii(ChannelHandlerContext ctx, String msg) throws Exception {
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

        // Registrar conexión si es nueva para este canal
        if (currentImei == null || !currentImei.equals(imei)) {
            this.currentImei = imei;
            connectionManager.register(imei, ctx.channel());
        }

        // Buscar dispositivo registrado con sus relaciones para evitar
        // LazyInitializationException
        Optional<Gps> gpsOpt = gpsService.findByImeiWithVehiculo(imei);
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
            case "S20": // Respuesta a comandos de configuración
                log.info("Respuesta de configuración recibida de IMEI {}: {}", imei, msg);
                procesarRespuestaConfiguracion(gps, cleanMsg);
                break;
            default:
                log.info("Mensaje de tipo {} recibido de IMEI {}: {}", messageType, imei, msg);
        }
    }

    /**
     * Procesa las respuestas a comandos (S20)
     */
    private void procesarRespuestaConfiguracion(Gps gps, String cleanMsg) {
        try {
            String content = cleanMsg.toUpperCase();
            log.info("Analizando respuesta de configuración para IMEI {}: {}", gps.getImei(), cleanMsg);

            boolean isOk = content.contains("OK") || content.contains("SUCCESS") || content.contains("SET OK");

            if (isOk) {
                if (content.contains("940")) {
                    gpsService.updateMotorBloqueado(gps.getId(), true);
                    log.info("Confirmado vía código: Motor BLOQUEADO para IMEI {}", gps.getImei());
                } else if (content.contains("941")) {
                    gpsService.updateMotorBloqueado(gps.getId(), false);
                    log.info("Confirmado vía código: Motor DESBLOQUEADO para IMEI {}", gps.getImei());
                } else if (content.contains("SLEEP")) {
                    boolean enabled = content.contains(" 1") || content.contains(" 5") || content.contains("ON");
                    gpsService.updateModoSueno(gps.getId(), enabled);
                    log.info("Confirmado vía código: Modo Sueño {} para IMEI {}", enabled ? "ACTIVADO" : "DESACTIVADO",
                            gps.getImei());
                } else if (content.contains("805") || content.contains("809") || content.contains("UPLOAD")) {
                    log.info("Confirmado vía código: Intervalo de reporte actualizado para IMEI {}", gps.getImei());
                } else if (content.contains("803")) {
                    log.info("Confirmado vía código: APN actualizado para IMEI {}", gps.getImei());
                } else if (content.contains("SET OK")) {
                    log.info("Configuración aceptada (SET OK) por el dispositivo IMEI {}", gps.getImei());
                } else {
                    log.info("Respuesta de configuración recibida (contenido no específico) para IMEI {}: {}",
                            gps.getImei(), cleanMsg);
                }
            } else {
                log.warn("Respuesta de configuración recibida pero no indica éxito para IMEI {}: {}", gps.getImei(),
                        cleanMsg);
            }
        } catch (Exception e) {
            log.error("Error procesando respuesta de configuración para IMEI {}: {}", gps.getImei(), e.getMessage());
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

            // Procesar alertas PUSH basadas en la telemetría recibida
            procesarAlertasPush(gps, saved, alarma);

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
     * Evalúa la telemetría recibida y dispara notificaciones push utilizando el
     * sistema genérico del backend y las preferencias de usuario.
     * Solo envía push si la bandera correspondiente está activada en la BD.
     */
    private void procesarAlertasPush(Gps gps, Telemetria telemetria, String alarma) {
        if (pushNotificationService == null || gps == null || telemetria == null) {
            return;
        }

        String vehiculoDesc = gps.getVehiculo() != null && gps.getVehiculo().getModelo() != null
                ? gps.getVehiculo().getModelo().getDescripcion()
                : "Vehículo";

        Integer velocidad = telemetria.getVelocidad();
        Boolean ignicionActual = telemetria.getIgnicion();
        Boolean ignicionAnterior = gps.getUltimaIgnicion();

        // Exceso de velocidad - solo si alertaVelocidad está habilitada
        if (Boolean.TRUE.equals(gps.getAlertaVelocidad())) {
            Integer limite = gps.getVelocidadLimite() != null ? gps.getVelocidadLimite() : 100;
            if (velocidad != null && velocidad > limite) {
                String titulo = "Alerta de velocidad (" + vehiculoDesc + ")";
                String mensaje = "Exceso de velocidad: " + velocidad + " km/h (límite: " + limite + " km/h) - IMEI "
                        + gps.getImei();
                String data = "{\"imei\":\"" + gps.getImei() + "\",\"tipo\":\"GPS_EXCESO_VELOCIDAD\"}";
                pushNotificationService.enviarAlertaTipo("GPS_EXCESO_VELOCIDAD", titulo, mensaje, data);
            }
        }

        // Choque / vibración - solo si alertaVibracion está habilitada
        if (Boolean.TRUE.equals(gps.getAlertaVibracion()) && "SHOCK".equalsIgnoreCase(alarma)) {
            String titulo = "Alerta de choque/vibración (" + vehiculoDesc + ")";
            String mensaje = "Se detectó choque o vibración en el GPS IMEI " + gps.getImei();
            String data = "{\"imei\":\"" + gps.getImei() + "\",\"tipo\":\"GPS_SHOCK\"}";
            pushNotificationService.enviarAlertaTipo("GPS_SHOCK", titulo, mensaje, data);
        }

        // Batería baja - solo si alertaBateriaBaja está habilitada
        if (Boolean.TRUE.equals(gps.getAlertaBateriaBaja()) && "LOW_BATTERY".equalsIgnoreCase(alarma)) {
            String titulo = "Batería baja en GPS (" + vehiculoDesc + ")";
            String mensaje = "El rastreador reporta batería baja - IMEI " + gps.getImei();
            String data = "{\"imei\":\"" + gps.getImei() + "\",\"tipo\":\"GPS_BATERIA_BAJA\"}";
            pushNotificationService.enviarAlertaTipo("GPS_BATERIA_BAJA", titulo, mensaje, data);
        }

        // Cambio de ignición (ACC ON/OFF) - solo si alertaAcc está habilitada
        if (Boolean.TRUE.equals(gps.getAlertaAcc()) && ignicionActual != null && ignicionAnterior != null
                && !ignicionActual.equals(ignicionAnterior)) {
            String tipo = ignicionActual ? "GPS_IGNICION_ON" : "GPS_IGNICION_OFF";
            String estado = ignicionActual ? "ENCENDIDO" : "APAGADO";
            String titulo = "Cambio de ignición (" + vehiculoDesc + ")";
            String mensaje = "El vehículo se ha " + (ignicionActual ? "encendido" : "apagado") + " - IMEI "
                    + gps.getImei();
            String data = "{\"imei\":\"" + gps.getImei() + "\",\"tipo\":\"" + tipo + "\",\"estado\":\"" + estado
                    + "\"}";
            pushNotificationService.enviarAlertaTipo(tipo, titulo, mensaje, data);
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

    private String parseAlarmaFromState(String stateStr) {
        if (stateStr == null || stateStr.isEmpty())
            return "NORMAL";
        try {
            long state = Long.parseLong(stateStr, 16);

            if ((state & 0x02) != 0)
                return "SOS";
            if ((state & 0x04) != 0)
                return "POWER_CUT";
            if ((state & 0x08) != 0)
                return "SHOCK";
            if ((state & 0x10) != 0)
                return "LOW_BATTERY";

        } catch (Exception e) {
            log.debug("Error parseando estado hex {}: {}", stateStr, e.getMessage());
        }
        return "NORMAL";
    }

    private Boolean parseIgnicion(String stateStr) {
        if (stateStr == null || stateStr.isEmpty())
            return false;
        try {
            long state = Long.parseLong(stateStr, 16);
            return (state & 0x01) != 0;
        } catch (Exception e) {
            log.debug("Error parseando ignición hex {}: {}", stateStr, e.getMessage());
            return false;
        }
    }

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
