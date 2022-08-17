package com.franco.dev.rabbit;

import com.franco.dev.service.empresarial.SucursalService;
import net.sf.jasperreports.engine.xml.JRPenFactory;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Component
public class RabbitMQConection {

    @Autowired
    private Environment env;

    @Autowired
    private SucursalService sucursalService;

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
        TopicExchange exchange = this.topicExchange();
        DirectExchange exchangeDirect = this.directExchange();
        Binding binding = this.binding(filaProducto, exchange, SERVIDOR_KEY);
        Binding binding3 = this.bindingDirect(filaProductoReplyTo, exchangeDirect, filaProductoReplyTo.getName());
        this.amqpAdmin.declareQueue(filaProducto);
        this.amqpAdmin.declareQueue(filaProductoReplyTo);
        this.amqpAdmin.declareExchange(exchange);
        this.amqpAdmin.declareBinding(binding);
        this.amqpAdmin.declareBinding(binding3);
    }
}
