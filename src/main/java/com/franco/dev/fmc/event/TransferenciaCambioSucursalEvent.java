package com.franco.dev.fmc.event;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.operaciones.Transferencia;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

public class TransferenciaCambioSucursalEvent extends ApplicationEvent {
    @Getter
    private final Transferencia transferencia;
    @Getter
    private final Sucursal oldSucursalOrigen;
    @Getter
    private final Sucursal oldSucursalDestino;

    public TransferenciaCambioSucursalEvent(Object source, Transferencia transferencia, Sucursal oldSucursalOrigen,
            Sucursal oldSucursalDestino) {
        super(source);
        this.transferencia = transferencia;
        this.oldSucursalOrigen = oldSucursalOrigen;
        this.oldSucursalDestino = oldSucursalDestino;
    }
}
