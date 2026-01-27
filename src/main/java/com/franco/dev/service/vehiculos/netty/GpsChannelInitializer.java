package com.franco.dev.service.vehiculos.netty;

import com.franco.dev.service.vehiculos.GpsService;
import com.franco.dev.service.vehiculos.TelemetriaService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GpsChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private GpsService gpsService;

    @Autowired
    private TelemetriaService telemetriaService;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new H02ProtocolDecoder());
        pipeline.addLast(new StringEncoder(CharsetUtil.US_ASCII));
        // Se crea una nueva instancia por cada conexión para seguridad de
        // encadenamiento,
        // pasándole los servicios singleton.
        pipeline.addLast(new GpsNettyHandler(gpsService, telemetriaService));
    }
}
