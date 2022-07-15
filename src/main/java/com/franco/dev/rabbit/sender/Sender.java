package com.franco.dev.rabbit.sender;

import com.franco.dev.rabbit.RabbitEntity;
import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.config.MessagingConfig;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.UUID;

@Service
public class Sender<T> {

    private static final Logger log = LoggerFactory.getLogger(Sender.class);

    @Autowired
    private RabbitTemplate template;

//    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }

    public void enviar(String key, RabbitDto<T> p){
        log.info("Enviando a exchange: "+RabbitMQConection.NOME_EXCHANGE+" key: "+key);
        template.convertAndSend(RabbitMQConection.NOME_EXCHANGE, key, p);
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p){
        return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key+".reply.to", p);
    }

}
