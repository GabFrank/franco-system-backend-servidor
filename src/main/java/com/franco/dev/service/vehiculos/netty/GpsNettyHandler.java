package com.franco.dev.service.vehiculos.netty;

import com.franco.dev.domain.vehiculos.Gps;
import com.franco.dev.domain.vehiculos.Telemetria;
import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.TelemetriaService;
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

    public GpsNettyHandler(GpsService gpsService, TelemetriaService telemetriaService) {
        this.gpsService = gpsService;
        this.telemetriaService = telemetriaService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        log.info("Mensaje Recibido: {}", msg);

        // Limpieza básica
        String cleanMsg = msg.trim();
        if (cleanMsg.endsWith("#")) {
            cleanMsg = cleanMsg.substring(0, cleanMsg.length() - 1);
        }

        String[] parts = cleanMsg.split(",");

        // Validación básica de longitud H02
        if (parts.length < 10) {
            log.warn("Mensaje ignorado por longitud insuficiente: {}", cleanMsg);
            return;
        }

        String header = parts[0];
        if (!header.contains("HQ")) {
            return;
        }

        String imei = parts[1];

        Optional<Gps> gpsOpt = gpsService.findByImei(imei);
        if (!gpsOpt.isPresent()) {
            log.warn("Dispositivo no encontrado con IMEI: {}", imei);
            return;
        }

        Gps gps = gpsOpt.get();

        try {
            // Extracción de datos
            String timeStr = parts[3]; // HHMMSS
            String validStr = parts[4]; // A o V
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
            Double lat = convertCoordinates(latStr, latDir);
            Double lon = convertCoordinates(lonStr, lonDir);

            // Parsing Velocidad (asumiendo que viene en km/h o knots, lo guardamos como
            // entero)
            Double speedVal = Double.parseDouble(speedStr);
            // ST-901 suele mandar km/h. Si fuera knots, habría que multiplicar por 1.852.
            // Para este ejemplo asumimos km/h directo como entero.
            Integer velocidad = speedVal.intValue();

            // Dirección
            Integer direccion = Integer.parseInt(dirStr);
            Boolean ignicion = false;

            Telemetria telemetria = new Telemetria();
            telemetria.setDispositivo(gps);
            telemetria.setFechaGps(fechaGps);
            telemetria.setFechaServidor(LocalDateTime.now());
            telemetria.setLatitud(lat);
            telemetria.setLongitud(lon);
            telemetria.setVelocidad(velocidad);
            telemetria.setDireccion(direccion);
            telemetria.setIgnicion(ignicion);
            telemetria.setAlarma(validStr.equals("A") ? "NORMAL" : "GPS_INVALID");
            telemetria.setJsonData("{\"raw\": \"" + msg + "\", \"state\": \"" + stateStr + "\"}");

            telemetriaService.save(telemetria);
            log.info("Telemetria guardada para IMEI: {}", imei);

        } catch (Exception e) {
            log.error("Error procesando telemetria: ", e);
        }
    }

    private Double convertCoordinates(String rawCoordinate, String direction) {
        if (rawCoordinate == null || rawCoordinate.isEmpty()) {
            return 0.0;
        }

        try {
            double rawVal = Double.parseDouble(rawCoordinate);
            int degrees = (int) (rawVal / 100);
            double minutes = rawVal - (degrees * 100);
            double decimalDegrees = degrees + (minutes / 60);

            if ("S".equalsIgnoreCase(direction) || "W".equalsIgnoreCase(direction) || "O".equalsIgnoreCase(direction)) {
                decimalDegrees = decimalDegrees * -1;
            }

            return decimalDegrees;
        } catch (NumberFormatException e) {
            log.warn("Error parseando coordenada: {}", rawCoordinate);
            return 0.0;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error en canal GPS: ", cause);
        ctx.close();
    }
}
