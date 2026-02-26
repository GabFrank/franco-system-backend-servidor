package com.franco.dev.fmc.event;

import com.franco.dev.domain.financiero.VentaCredito;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class VentaCreditoRealizadaEvent extends ApplicationEvent {
    @Getter
    private final VentaCredito ventaCredito;

    public VentaCreditoRealizadaEvent(Object source, VentaCredito ventaCredito) {
        super(source);
        this.ventaCredito = ventaCredito;
    }
}
