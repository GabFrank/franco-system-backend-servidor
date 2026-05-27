package com.franco.dev.service.activos.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class GpsChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private GpsConnectionManager connectionManager;

    @Autowired
    private GpsAsyncProcessor gpsAsyncProcessor;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast("idleStateHandler",
                new IdleStateHandler(5, 0, 0, TimeUnit.MINUTES));

        pipeline.addLast("h02Decoder", new H02ProtocolDecoder());

        pipeline.addLast("stringEncoder", new StringEncoder(CharsetUtil.US_ASCII));

        pipeline.addLast("gpsHandler",
                new GpsNettyHandler(connectionManager, gpsAsyncProcessor));
    }
}
