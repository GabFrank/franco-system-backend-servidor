package com.franco.dev.service.activos.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class H02ProtocolDecoder extends ByteToMessageDecoder {

    // Límite defensivo para evitar procesar bloques enormes de basura/binario
    private static final int MAX_FRAME_LENGTH = 512;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Marcamos la posición actual por si no tenemos un mensaje completo
        in.markReaderIndex();

        // Buscamos bytes legibles mínimos
        if (in.readableBytes() < 5) {
            return;
        }

        int endIndex = indexOf(in, (byte) '#');

        if (endIndex == -1) {
            // No encontramos el final, esperamos más datos.
            in.resetReaderIndex();
            return;
        }

        // Calculamos longitud
        int length = endIndex - in.readerIndex() + 1;

        // Si el mensaje es excesivamente grande es muy probable que sea basura/binario
        if (length <= 0 || length > MAX_FRAME_LENGTH) {
            int readable = Math.min(length, in.readableBytes());
            if (readable > 0) {
                byte[] discarded = new byte[readable];
                in.readBytes(discarded);
                log.debug("Frame H02 descartado por longitud inválida ({} bytes) desde {}",
                        length, ctx.channel().remoteAddress());
            } else {
                in.resetReaderIndex();
            }
            return;
        }

        // Leemos el mensaje completo
        ByteBuf frame = in.readBytes(length);
        try {
            // Intento 1: ASCII "normal" del protocolo (*HQ,...#)
            String ascii = frame.toString(StandardCharsets.US_ASCII);
            if (ascii.startsWith("*HQ")) {
                out.add(ascii);
                return;
            }

            // Intento 2: frame binario (muchos equipos envían paquetes que NO son ASCII)
            byte[] bytes = new byte[frame.readableBytes()];
            frame.getBytes(frame.readerIndex(), bytes);
            out.add(bytes);
        } finally {
            frame.release();
        }
    }

    private int indexOf(ByteBuf needle, byte value) {
        for (int i = needle.readerIndex(); i < needle.writerIndex(); i++) {
            if (needle.getByte(i) == value) {
                return i;
            }
        }
        return -1;
    }
}
