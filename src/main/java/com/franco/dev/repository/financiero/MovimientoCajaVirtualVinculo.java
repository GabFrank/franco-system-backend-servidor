package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.enums.CajaVirtualTipoMovimiento;
import com.franco.dev.domain.financiero.enums.OrigenMovimientoTipo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Lo que hace falta saber de un movimiento de caja para decidir cómo se anula, leído por proyección:
 * sin cargar la entidad, para que el {@code lockById} que viene después sea su primera carga y traiga
 * el estado real (ver {@link MovimientoCajaVirtualRepository#lockById}).
 */
@Getter
@AllArgsConstructor
public class MovimientoCajaVirtualVinculo {

    private final Long id;
    private final CajaVirtualTipoMovimiento tipoMovimiento;
    private final OrigenMovimientoTipo origenTipo;
    private final Long cajaVirtualId;
    private final Long cajaOrigenId;
    private final Long cajaDestinoId;
    private final Long monedaId;
    private final Double cantidad;
    private final Long referenciaId;
    private final LocalDateTime creadoEn;

    /**
     * Una pata de una transferencia entre cajas hecha con {@code TesoreriaService.transferir}. Las de
     * una operación financiera llevan su propio origen y se anulan desde la operación. El origen nulo
     * cuenta: así quedaron las transferencias anteriores a que existiera la columna.
     */
    public boolean esPataDeTransferencia() {
        boolean tipoDeTransferencia = tipoMovimiento == CajaVirtualTipoMovimiento.TRANSFERENCIA_ENTRADA
                || tipoMovimiento == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA;
        return tipoDeTransferencia && (origenTipo == null || origenTipo == OrigenMovimientoTipo.MANUAL);
    }

    public boolean esSalida() {
        return tipoMovimiento == CajaVirtualTipoMovimiento.TRANSFERENCIA_SALIDA;
    }

    /**
     * ¿{@code otra} es la otra pata de la misma transferencia? Tipo opuesto, mismas cajas, moneda y
     * monto, cada una en su caja, y las dos apuntándose entre sí. El vínculo solo no alcanza:
     * {@code saveMovimientoCajaVirtual} acepta el {@code referenciaId} que mande el cliente.
     */
    public boolean esContraparteDe(MovimientoCajaVirtualVinculo otra) {
        if (otra == null || !esPataDeTransferencia() || !otra.esPataDeTransferencia()) return false;
        if (esSalida() == otra.esSalida()) return false;
        MovimientoCajaVirtualVinculo salida = esSalida() ? this : otra;
        MovimientoCajaVirtualVinculo entrada = esSalida() ? otra : this;
        return cajaOrigenId != null && cajaDestinoId != null
                && cajaOrigenId.equals(otra.cajaOrigenId) && cajaDestinoId.equals(otra.cajaDestinoId)
                && cajaOrigenId.equals(salida.cajaVirtualId) && cajaDestinoId.equals(entrada.cajaVirtualId)
                && java.util.Objects.equals(monedaId, otra.monedaId)
                && cantidad != null && cantidad.equals(otra.cantidad)
                && otra.id.equals(referenciaId) && id.equals(otra.referenciaId);
    }
}
