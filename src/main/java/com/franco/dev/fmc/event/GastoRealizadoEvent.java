package com.franco.dev.fmc.event;

import com.franco.dev.domain.financiero.Gasto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class GastoRealizadoEvent extends ApplicationEvent {
    @Getter
    private final Gasto gasto;

    public GastoRealizadoEvent(Object source, Gasto gasto) {
        super(source);
        this.gasto = gasto;
    }
}
