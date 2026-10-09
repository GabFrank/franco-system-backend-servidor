package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.enums.EstadoRetiro;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Dónde está parado un retiro frente a la caja mayor, leído por proyección: después del lock y de la
 * base, no de una instancia que la request ya tuviera cargada (regla del módulo, ARQUITECTURA §7).
 */
@Getter
@AllArgsConstructor
public class RetiroSituacion {

    private final EstadoRetiro estado;
    private final Long movimientoCajaVirtualId;
    private final Long cajaVirtualId;

    public boolean estaCancelado() {
        return estado == EstadoRetiro.CANCELADO;
    }
}
