package com.franco.dev.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración de WebSocket utilizando STOMP para mensajería en tiempo real.
 * Utilizado principalmente para:
 * - Actualizaciones de telemetría GPS en tiempo real
 * - Notificaciones de eventos del sistema
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilitar broker simple para enviar mensajes a clientes
        // Los clientes se suscriben a /topic/...
        config.enableSimpleBroker("/topic");

        // Prefijo para mensajes desde el cliente al servidor
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint para conexión WebSocket con SockJS fallback
        registry.addEndpoint("/ws/gps")
                .setAllowedOrigins("*")
                .withSockJS();

        // Endpoint sin SockJS para clientes nativos
        registry.addEndpoint("/ws/gps")
                .setAllowedOrigins("*");
    }
}
