package com.franco.dev.domain.operaciones.dto;

import com.franco.dev.domain.operaciones.enums.EstadoLote;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Saldo de un lote expresado en la presentacion con la que trabaja el operador.
 *
 * El stock por lote se lleva en UNIDADES, pero las pantallas de transferencia cargan en
 * PRESENTACIONES (cajas, packs). La conversion se hace aca y no en el frontend por dos razones:
 * es una regla del negocio, y tiene que ser exactamente la misma que se aplica al persistir la
 * asignacion. Dos implementaciones de la misma division terminan divergiendo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockLotePresentacionDto {
    private Long loteId;
    private String numeroLote;
    private LocalDate fechaVencimiento;
    /** Fecha por la que ordena FEFO. */
    private LocalDate fechaRetiro;
    private EstadoLote estado;
    /** Saldo en unidades, como vive en el ledger. */
    private Double cantidadDisponible;
    /**
     * Presentaciones COMPLETAS que entran en el saldo. Una caja de 6 es indivisible: de 65
     * unidades salen 10 cajas, no 10,833.
     */
    private Double cantidadDisponiblePresentacion;
    /** Unidades que sobran fuera de las presentaciones completas. */
    private Double unidadesSobrantes;
    /** Unidades que vale una presentacion. 1 cuando no se pidio ninguna. */
    private Double unidadesPorPresentacion;
    /** Nombre de la presentacion, para que la pantalla no tenga que resolverlo aparte. */
    private String presentacionDescripcion;
}
