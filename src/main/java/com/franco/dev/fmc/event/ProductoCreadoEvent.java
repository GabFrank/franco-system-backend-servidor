package com.franco.dev.fmc.event;

import com.franco.dev.domain.productos.Producto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class ProductoCreadoEvent extends ApplicationEvent {
    @Getter
    private final Producto producto;

    public ProductoCreadoEvent(Object source, Producto producto) {
        super(source);
        this.producto = producto;
    }
}
