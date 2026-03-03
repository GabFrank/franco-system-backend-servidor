package com.franco.dev.fmc.event;

import com.franco.dev.domain.operaciones.Inventario;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class InventarioIniciadoEvent extends ApplicationEvent {
    @Getter
    private final Inventario inventario;

    public InventarioIniciadoEvent(Object source, Inventario inventario) {
        super(source);
        this.inventario = inventario;
    }
}
