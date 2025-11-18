package com.franco.dev.fmc.config;

import java.util.concurrent.Executor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PushNotificationConfig {

    public static final String PUSH_NOTIFICATION_QUEUE = "push-notification.queue";

    @Bean
    public Queue pushNotificationQueue() {
        return QueueBuilder.durable(PUSH_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public MessageConverter pushNotificationMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate pushNotificationRabbitTemplate(ConnectionFactory connectionFactory,
                                                         MessageConverter pushNotificationMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(pushNotificationMessageConverter);
        return rabbitTemplate;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-notification-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }
}


