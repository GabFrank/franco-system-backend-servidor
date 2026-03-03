package com.franco.dev.fmc.event;

import com.franco.dev.domain.financiero.Retiro;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class RetiroRealizadoEvent extends ApplicationEvent {
    @Getter
    private final Retiro retiro;

    public RetiroRealizadoEvent(Object source, Retiro retiro) {
        super(source);
        this.retiro = retiro;
    }
}
