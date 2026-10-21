package com.franco.dev.graphql.financiero.publisher;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Señal de que cambió el saldo de una caja virtual o de una cuenta bancaria. Va lleno uno de los dos ids.
 *
 * No lleva montos ni movimientos: el desktop que la recibe vuelve a leer la caja con las consultas de
 * siempre, que aplican rol y ACL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TesoreriaCambio {

    private Long cajaVirtualId;

    private Long cuentaBancariaId;

    public static TesoreriaCambio deCaja(Long cajaVirtualId) {
        return new TesoreriaCambio(cajaVirtualId, null);
    }

    public static TesoreriaCambio deCuenta(Long cuentaBancariaId) {
        return new TesoreriaCambio(null, cuentaBancariaId);
    }
}
