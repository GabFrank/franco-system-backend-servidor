package com.franco.dev.fmc.event;

import com.franco.dev.domain.financiero.Cambio;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class CotizacionActualizadaEvent extends ApplicationEvent {
    @Getter
    private final Cambio cambio;

    public CotizacionActualizadaEvent(Object source, Cambio cambio) {
        super(source);
        this.cambio = cambio;
    }
}
