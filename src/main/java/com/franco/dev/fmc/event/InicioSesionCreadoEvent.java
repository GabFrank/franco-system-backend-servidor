package com.franco.dev.fmc.event;

import com.franco.dev.domain.configuracion.InicioSesion;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class InicioSesionCreadoEvent extends ApplicationEvent {
    @Getter
    private final InicioSesion inicioSesion;

    public InicioSesionCreadoEvent(Object source, InicioSesion inicioSesion) {
        super(source);
        this.inicioSesion = inicioSesion;
    }
}
