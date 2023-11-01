package com.franco.dev.rabbit.sender;

import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
public class Sender<T> {

    private static final Logger log = LoggerFactory.getLogger(Sender.class);

    @Autowired
    private RabbitTemplate template;

//    public void send(RabbitDto<T> p, String key) {
//        template.convertAndSend(MessagingConfig.EXCHANGE, key, p);
//    }

    public void enviar(String key, RabbitDto<T> p, Boolean direct) {
        log.info("Enviando a exchange: " + (direct == true ? RabbitMQConection.NOME_EXCHANGE_DIRECT : RabbitMQConection.NOME_EXCHANGE) + " key: " + key);
        template.convertAndSend(RabbitMQConection.NOME_EXCHANGE, key, p);
    }

    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
        log.info("Enviando a exchange: " + RabbitMQConection.NOME_EXCHANGE + " key: " + key);
        template.setReplyTimeout(10000);
        return template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", p);
    }

//    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
////        Object response = template.convertSendAndReceive(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", p);
////        return response;
//        MessageProperties props = new MessageProperties();
//            props.setCorrelationId("108");
//            props.setReplyTo("servidor.reply");
//        Message message = template.getMessageConverter().toMessage(p, props);
//        template.send(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", message);
//        SimpleMessageListenerContainer container = new
//                SimpleMessageListenerContainer();
//        container.setConnectionFactory(template.getConnectionFactory());
//        container.setQueueNames("servidor.reply");
//        container.setMessageListener(new MessageListener() {
//            @Override
//            public void onMessage(Message message) {
//                returnMessage(template.getMessageConverter().fromMessage(message));
//            }
//
//            Object returnMessage(Object obj){
//                return obj;
//            }
//        });
//        container.start();
//        return null;
//    }

//    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
//        UUID uuid = UUID.randomUUID();
//        MessageProperties props = new MessageProperties();
//        props.setReplyTo("servidor.reply."+key);
//        props.setCorrelationId(uuid.toString());
//        Message message = template.getMessageConverter().toMessage(p, props);
//        template.send(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", message);
//
//        CompletableFuture<Object> future = new CompletableFuture<>();
//
//        MessageListener listener = new MessageListener() {
//            @Override
//            public void onMessage(Message message) {
//                if (message.getMessageProperties().getCorrelationId().equals(uuid.toString())) {
//                    future.complete(template.getMessageConverter().fromMessage(message));
//                }
//            }
//        };
//
//        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
//        container.setConnectionFactory(template.getConnectionFactory());
//        container.setQueueNames("servidor.reply."+key);
//        container.setMessageListener(listener);
//        container.setExclusive(true);
//        container.start();
//
//        Object response;
//        try {
//            response = future.get(); // wait for the response message
//            container.stop(); // stop the container
//        } catch (InterruptedException | ExecutionException e) {
//            response = null;
//            container.stop(); // stop the container
//        }
//
//        return response;
//    }

//    public Object enviarAndRecibir(String key, RabbitDto<T> p) {
//        String replyTo = "servidor.reply." + key;
//        String correlationId = UUID.randomUUID().toString();
//        MessageProperties props = new MessageProperties();
//        props.setReplyTo(replyTo);
//        props.setCorrelationId(correlationId);
//        Message message = template.getMessageConverter().toMessage(p, props);
//
//        // Send the message and wait for a response
//        template.send(RabbitMQConection.NOME_EXCHANGE_DIRECT, key + ".reply.to", message);
//
//        // Set up a listener to receive the response message
//        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
//        container.setConnectionFactory(template.getConnectionFactory());
//        container.setQueueNames(replyTo);
//        container.setMessageListener(new ResponseMessageListener(correlationId));
//
//        // Start the listener container
//        container.start();
//
//        // Wait for the response message
//        Message response = ResponseMessageListener.getResponse(correlationId);
//
//        // Stop the listener container
//        container.stop();
//
//        // Return the response payload
//        return template.getMessageConverter().fromMessage(response);
//    }
//
//    private static class ResponseMessageListener implements MessageListener {
//
//        private final String correlationId;
//        CompletableFuture<Object> future = new CompletableFuture<>();
//
//
//        public ResponseMessageListener(String correlationId) {
//            this.correlationId = correlationId;
//        }
//
//        public static Message getResponse(String correlationId) {
//
//            return response;
//        }
//
//        @Override
//        public void onMessage(Message message) {
//            if (message.getMessageProperties().getCorrelationId().equals(correlationId)) {
//                future.complete(message);
//            }
//        }
//    }

}
