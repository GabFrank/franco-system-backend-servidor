package com.franco.dev.service.vehiculos.netty;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GpsNettyHandler extends SimpleChannelInboundHandler<Object> {

    private final GpsConnectionManager connectionManager;
    private final GpsAsyncProcessor gpsAsyncProcessor;
    private String currentImei;

    public GpsNettyHandler(GpsConnectionManager connectionManager, GpsAsyncProcessor gpsAsyncProcessor) {
        this.connectionManager = connectionManager;
        this.gpsAsyncProcessor = gpsAsyncProcessor;
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

        log.warn("Mensaje GPS de tipo no soportado desde {}", ctx.channel().remoteAddress());
    }

    private void handleBinary(ChannelHandlerContext ctx, byte[] bytes) {
        String hex = ByteBufUtil.hexDump(bytes);
        log.info("Frame binario recibido ({} bytes) desde {}: {}",
                bytes.length, ctx.channel().remoteAddress(), hex);

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
                    log.error("Error procesando *HQ extraído: {}", e.getMessage());
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
        if (parts.length < 3 || !parts[0].contains("HQ")) {
            log.debug("Mensaje ignorado (no H02 o muy corto): {}", cleanMsg);
            return;
        }

        String imei = parts[1];
        String messageType = parts[2];

        // Registrar conexión de forma síncrona para mantener el canal vivo en el gestor
        if (currentImei == null || !currentImei.equals(imei)) {
            this.currentImei = imei;
            connectionManager.register(imei, ctx.channel());
        }

        // Delegar TODO lo que implique DB o lógica compleja al hilo asíncrono
        gpsAsyncProcessor.processMessage(imei, messageType, parts, msg);

        // Envío de ACKs síncronos (operación de red rápida) para cumplir con el protocolo
        if ("BP00".equals(messageType) || "BP05".equals(messageType) || "V1".equals(messageType)) {
            enviarAck(ctx, imei, messageType.startsWith("BP") ? messageType : "V1");
        }
    }

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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error en canal GPS desde {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}
