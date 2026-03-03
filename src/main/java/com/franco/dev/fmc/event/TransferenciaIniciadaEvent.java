package com.franco.dev.fmc.event;

import com.franco.dev.domain.operaciones.Transferencia;
import org.springframework.context.ApplicationEvent;

public class TransferenciaIniciadaEvent extends ApplicationEvent {
    private final Transferencia transferencia;

    public TransferenciaIniciadaEvent(Object source, Transferencia transferencia) {
        super(source);
        this.transferencia = transferencia;
    }

    public Transferencia getTransferencia() {
        return transferencia;
    }
}
