package com.franco.dev.rabbit;

import com.franco.dev.service.empresarial.SucursalService;
import com.rabbitmq.client.ShutdownSignalException;
import net.sf.jasperreports.engine.xml.JRPenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Component
public class RabbitMQConection {

    private final Logger logger = LoggerFactory.getLogger(RabbitMQConection.class);

    @Autowired
    private Environment env;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private CachingConnectionFactory cachingConnectionFactory;

    public static final String NOME_EXCHANGE = "amq.topic";
    public static final String NOME_EXCHANGE_DIRECT = "amq.direct";
    public static final String FILIAL_KEY = "filial";
    public static final String SERVIDOR_KEY = "servidor";
    private AmqpAdmin amqpAdmin;

    public RabbitMQConection(AmqpAdmin amqpAdmin){
        this.amqpAdmin = amqpAdmin;
    }

    private Queue fila(String name){
        return new Queue(name, true, false, false);
    }

    private TopicExchange topicExchange(){
        return new TopicExchange(NOME_EXCHANGE);
    }

    private DirectExchange directExchange() { return  new DirectExchange(NOME_EXCHANGE_DIRECT); }

    private Binding binding(Queue fila, TopicExchange exchange, String key){
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    private Binding bindingDirect(Queue fila, DirectExchange exchange, String key){
        return new Binding(fila.getName(), Binding.DestinationType.QUEUE, exchange.getName(), key, null);
    }

    @PostConstruct
    private void add(){
        Queue filaProducto = this.fila(SERVIDOR_KEY);
        Queue filaProductoReplyTo = this.fila(SERVIDOR_KEY+".reply.to");
        Queue replyQueue = this.fila("servidor.reply");
        TopicExchange exchange = this.topicExchange();
        DirectExchange exchangeDirect = this.directExchange();
        Binding binding = this.binding(filaProducto, exchange, SERVIDOR_KEY);
        Binding binding3 = this.bindingDirect(filaProductoReplyTo, exchangeDirect, filaProductoReplyTo.getName());
        Binding binding4 = this.bindingDirect(replyQueue, exchangeDirect, replyQueue.getName());
        ConnectionListener connectionListener = new ConnectionListener() {
            @Override
            public void onCreate(Connection connection) {
                logger.info("la conexcion con rabbit fue establecida");

            }

            @Override
            public void onClose(Connection connection) {
                logger.info("la conexcion con rabbit fue perdida");
            }

            @Override
            public void onShutDown(ShutdownSignalException signal) {
                logger.info("la conexcion con rabbit fue interrimpida");
            }
        };

        cachingConnectionFactory.addConnectionListener(connectionListener);
        cachingConnectionFactory.getRabbitConnectionFactory().setRequestedHeartbeat(1);
        try {
            this.amqpAdmin.declareQueue(filaProducto);
            this.amqpAdmin.declareQueue(filaProductoReplyTo);
            this.amqpAdmin.declareQueue(replyQueue);
            this.amqpAdmin.declareExchange(exchange);
            this.amqpAdmin.declareBinding(binding);
            this.amqpAdmin.declareBinding(binding3);
            this.amqpAdmin.declareBinding(binding4);
        } catch (AmqpConnectException e){
            e.printStackTrace();
        }

    }
}
