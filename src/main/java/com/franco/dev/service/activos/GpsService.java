package com.franco.dev.service.activos;

import com.franco.dev.domain.activos.Gps;
import com.franco.dev.repository.activos.GpsRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.activos.netty.GpsConnectionManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import com.franco.dev.service.activos.websocket.GpsTelemetriaWebSocketService;
import java.util.List;
import java.util.Optional;
import io.netty.channel.Channel;

@Slf4j
@Service
@AllArgsConstructor
public class GpsService extends CrudService<Gps, GpsRepository, Long> {

    private final GpsRepository repository;
    private final GpsConnectionManager connectionManager;
    private final GpsTelemetriaWebSocketService webSocketService;

    @Override
    public GpsRepository getRepository() {
        return repository;
    }

    public Optional<Gps> findByImei(String imei) {
        return repository.findByImei(imei);
    }

    public Optional<Gps> findByImeiWithVehiculo(String imei) {
        return repository.findByImeiWithVehiculo(imei);
    }

    public List<Gps> findByVehiculoId(Long vehiculoId) {
        return repository.findByVehiculoId(vehiculoId);
    }

    public List<Gps> findAllActivos() {
        return repository.findByActivoTrue();
    }

    public List<Gps> search(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return repository.findAllWithVehiculoFetched();
        }
        return repository.searchByTexto(texto.trim().toLowerCase());
    }

    @Transactional
    public void actualizarUltimaPosicion(Long gpsId, Double latitud, Double longitud,
            LocalDateTime fechaGps, Boolean ignicion) {
        try {
            repository.actualizarUltimaPosicion(gpsId, latitud, longitud, fechaGps, ignicion);
            log.debug("Caché de posición actualizada para GPS ID: {}", gpsId);
        } catch (Exception e) {
            log.error("Error actualizando caché de posición para GPS ID {}: {}", gpsId, e.getMessage());
        }
    }

    @Transactional
    public void updateModoSueno(Long gpsId, Boolean modoSueno) {
        repository.updateModoSueno(gpsId, modoSueno);
        log.info("Configuración de Modo Sueño actualizada para GPS ID {}: {}", gpsId, modoSueno);
        repository.findById(gpsId).ifPresent(webSocketService::broadcastGpsUpdate);
    }

    @Transactional
    public void updateIntervaloReporte(Long gpsId, Integer intervalo) {
        repository.updateIntervaloReporte(gpsId, intervalo);
        log.info("Configuración de Intervalo de Reporte actualizada para GPS ID {}: {}s", gpsId, intervalo);
        repository.findById(gpsId).ifPresent(webSocketService::broadcastGpsUpdate);
    }

    @Transactional
    public void updateMotorBloqueado(Long gpsId, Boolean bloqueado) {
        repository.updateMotorBloqueado(gpsId, bloqueado);
        log.info("Configuración de Bloqueo de Motor actualizada para GPS ID {}: {}", gpsId, bloqueado);
        repository.findById(gpsId).ifPresent(webSocketService::broadcastGpsUpdate);
    }

    @Transactional
    public Gps updateConfigAlertas(Long gpsId, Boolean alertaVelocidad, Integer velocidadLimite,
            Boolean alertaVibracion, Boolean alertaBateriaBaja, Boolean alertaAcc) {
        repository.updateConfigAlertas(gpsId, alertaVelocidad, velocidadLimite, alertaVibracion,
                alertaBateriaBaja, alertaAcc);
        log.info("Config de alertas push actualizada para GPS ID {}: vel={}, lim={}, vib={}, bat={}, acc={}",
                gpsId, alertaVelocidad, velocidadLimite, alertaVibracion, alertaBateriaBaja, alertaAcc);
        return repository.findById(gpsId).orElse(null);
    }

    public List<Gps> findAllConUltimaPosicion() {
        return repository.findAllWithUltimaPosicion();
    }

    /**
     * Envía un comando al dispositivo GPS vía socket TCP.
     */
    @Transactional
    public boolean enviarComando(Long gpsId, String tipo, String valor) {
        Optional<Gps> gpsOpt = repository.findById(gpsId);
        if (!gpsOpt.isPresent()) {
            log.error("GPS no encontrado con ID: {}", gpsId);
            return false;
        }

        Gps gps = gpsOpt.get();
        String imei = gps.getImei();
        Channel channel = connectionManager.getChannel(imei);

        if (channel == null || !channel.isActive()) {
            log.warn("Intento de enviar comando '{}' a GPS {} fallido: Dispositivo no conectado", tipo, imei);
            return false;
        }

        String comandoRaw = traducirComando(imei, tipo, valor);
        if (comandoRaw == null) {
            log.error("Tipo de comando no reconocido: {}", tipo);
            return false;
        }

        try {
            channel.writeAndFlush(comandoRaw); // H02 protocol uses # as terminator, no \r\n needed
            log.info("Comando enviado a IMEI {}: {}", imei, comandoRaw);

            // Actualizar estado localmente ya que el ACK no devuelve el valor
            if (tipo.equalsIgnoreCase("INTERVALO")) {
                try {
                    this.updateIntervaloReporte(gpsId, Integer.parseInt(valor));
                } catch (Exception e) {
                    log.error("Error actualizando intervalo provisional: {}", e.getMessage());
                }
            } else if (tipo.equalsIgnoreCase("MOTOR_ON")) {
                this.updateMotorBloqueado(gpsId, false);
            } else if (tipo.equalsIgnoreCase("MOTOR_OFF")) {
                this.updateMotorBloqueado(gpsId, true);
            } else if (tipo.equalsIgnoreCase("SLEEP_ON")) {
                this.updateModoSueno(gpsId, true);
            } else if (tipo.equalsIgnoreCase("SLEEP_OFF")) {
                this.updateModoSueno(gpsId, false);
            }

            return true;
        } catch (Exception e) {
            log.error("Error enviando comando a IMEI {}: {}", imei, e.getMessage());
            return false;
        }
    }

    /**
     * Traduce los comandos de la interfaz al protocolo SinoTrack/H02.
     * Basado en comandos estándar de SinoTrack.
     */
    private String traducirComando(String imei, String tipo, String valor) {
        String timestamp = DateTimeFormatter.ofPattern("HHmmss").format(LocalTime.now());
        String password = "0000"; // Password por defecto común en SinoTrack

        switch (tipo.toUpperCase()) {
            case "MOTOR_OFF":
                // 940 + password
                return String.format("*HQ,%s,S20,%s,940%s#", imei, timestamp, password);
            case "MOTOR_ON":
                // 941 + password
                return String.format("*HQ,%s,S20,%s,941%s#", imei, timestamp, password);
            case "SLEEP_ON":
                // SLEEP + password + Comma + T (5 minutos por defecto)
                return String.format("*HQ,%s,S20,%s,SLEEP%s,5#", imei, timestamp, password);
            case "SLEEP_OFF":
                // SLEEP + password + Comma + 0
                return String.format("*HQ,%s,S20,%s,SLEEP%s,0#", imei, timestamp, password);
            case "INTERVALO":
                // 805 + password + Comma + T (ACC ON interval)
                return String.format("*HQ,%s,S20,%s,805%s,%s#", imei, timestamp, password, valor);
            case "APN":
                // 803 + password + Comma + APN[,USER,PASS]
                return String.format("*HQ,%s,S20,%s,803%s,%s#", imei, timestamp, password, valor);
            default:
                return null;
        }
    }
}
