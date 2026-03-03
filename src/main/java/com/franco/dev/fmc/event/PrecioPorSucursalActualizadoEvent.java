package com.franco.dev.fmc.event;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class PrecioPorSucursalActualizadoEvent extends ApplicationEvent {
    @Getter
    private final PrecioPorSucursal precioPorSucursal;
    @Getter
    private final Double precioAnterior;

    public PrecioPorSucursalActualizadoEvent(Object source, PrecioPorSucursal precioPorSucursal,
            Double precioAnterior) {
        super(source);
        this.precioPorSucursal = precioPorSucursal;
        this.precioAnterior = precioAnterior;
    }
}
