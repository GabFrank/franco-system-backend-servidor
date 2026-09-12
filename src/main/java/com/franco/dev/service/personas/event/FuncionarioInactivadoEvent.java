package com.franco.dev.service.personas.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * El funcionario acaba de pasar de activo a inactivo.
 *
 * <p>Lo publica {@code FuncionarioService.save()} en la transicion, no cada camino por su
 * cuenta: al funcionario lo dan de baja el boton "Egresar", el pago del finiquito, el pago
 * por tesoreria y el toggle del legajo, y solo el primero pasa por {@code egresar()}. La
 * transicion es lo unico que todos comparten.</p>
 *
 * <p>Viaja el id y no la entidad: quien escucha necesita releer sus propios datos, no el
 * funcionario a medio guardar.</p>
 */
public class FuncionarioInactivadoEvent extends ApplicationEvent {

    @Getter
    private final Long funcionarioId;

    public FuncionarioInactivadoEvent(Object source, Long funcionarioId) {
        super(source);
        this.funcionarioId = funcionarioId;
    }
}
