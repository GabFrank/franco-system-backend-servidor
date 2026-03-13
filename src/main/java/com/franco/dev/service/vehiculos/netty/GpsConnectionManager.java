package com.franco.dev.service.vehiculos.netty;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class GpsConnectionManager {
    private final Map<String, Channel> activeConnections = new ConcurrentHashMap<>();

    public void register(String imei, Channel channel) {
        activeConnections.put(imei, channel);
        log.info("Canal registrado para IMEI: {}. Conexiones activas: {}", imei, activeConnections.size());
    }

    public void unregister(String imei) {
        activeConnections.remove(imei);
        log.info("Canal desregistrado para IMEI: {}. Conexiones activas: {}", imei, activeConnections.size());
    }

    public Channel getChannel(String imei) {
        return activeConnections.get(imei);
    }

    public boolean isConnected(String imei) {
        return activeConnections.containsKey(imei) && activeConnections.get(imei).isActive();
    }
}
