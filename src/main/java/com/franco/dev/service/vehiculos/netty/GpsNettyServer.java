package com.franco.dev.service.vehiculos.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;

@Slf4j
@Component
public class GpsNettyServer {

    @Value("${gps.server.port:5013}")
    private int port;

    @Autowired
    private GpsChannelInitializer gpsChannelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .localAddress(new InetSocketAddress(port))
                        .childHandler(gpsChannelInitializer)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                serverChannelFuture = b.bind().sync();
                log.info("Servidor GPS TCP iniciado en el puerto {}", port);
                serverChannelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Servidor GPS interrumpido", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error iniciando servidor GPS", e);
            } finally {
                stop();
            }
        }).start();
    }

    @PreDestroy
    public void stop() {
        log.info("Deteniendo servidor GPS...");
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
