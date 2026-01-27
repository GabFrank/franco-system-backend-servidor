package com.franco.dev.service.vehiculos.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class H02ProtocolDecoder extends ByteToMessageDecoder {

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

        // Leemos el mensaje completo
        ByteBuf frame = in.readBytes(length);

        // Convertimos a String
        String message = frame.toString(StandardCharsets.US_ASCII);

        // Liberamos el buffer frame
        frame.release();

        // Agregamos a la salida si parece válido (empieza con *HQ, aunque puede variar)
        // ST-901 suele enviar *HQ
        if (message.startsWith("*HQ")) {
            out.add(message);
        } else {
            System.out.println("Mensaje descartado (formato desconocido): " + message);
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
