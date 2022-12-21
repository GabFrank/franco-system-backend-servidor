package com.franco.dev.rabbit.receiver;

import com.franco.dev.rabbit.RabbitMQConection;
import com.franco.dev.rabbit.dto.RabbitDto;
import com.franco.dev.service.rabbitmq.PropagacionService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Receiver {

    private Logger log = LoggerFactory.getLogger(Receiver.class);

    @Autowired
    private PropagacionService propagacionService;

    @RabbitListener(queues = RabbitMQConection.SERVIDOR_KEY)
    public void receive(RabbitDto dto, final Channel channel) {
        log.info("recibiendo");
        log.info(String.valueOf(channel.getChannelNumber()));
        if (dto.getTipoAccion() != null) log.info(dto.getTipoAccion().name());
        if (dto.getTipoEntidad() != null) log.info(dto.getTipoEntidad().name());
        switch (dto.getTipoAccion()) {
            case SOLICITAR_DB:
                if (dto.getIdSucursalOrigen() != null) {
                    if (dto.getTipoEntidad() != null) {
                        propagacionService.propagarDB(dto);
                    } else {
                        log.info("transferencia de base de datos completa");
                        propagacionService.setSucursalConfigured(dto.getIdSucursalOrigen());
                    }
                }
                break;
            case GUARDAR:
            case DELETE:
                propagacionService.crudEntidad(dto);
                break;
            case SOLICITAR_RESOURCES:
                propagacionService.enviarResources(dto);
                break;
        }
    }

    @RabbitListener(queues = RabbitMQConection.SERVIDOR_KEY+".reply.to")
    public Object receiveAndReply(RabbitDto dto) {
        switch (dto.getTipoAccion()) {
            case GUARDAR:
                return propagacionService.crudEntidad(dto);
            default:
                return null;
        }
    }
}
