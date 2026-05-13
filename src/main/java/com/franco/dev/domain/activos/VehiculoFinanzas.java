package com.franco.dev.domain.activos;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.personas.Proveedor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vehiculo_finanzas", schema = "vehiculos")
public class VehiculoFinanzas {

    @Id
    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "situacion_pago")
    private String situacionPago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_id")
    private Moneda moneda;

    @Column(name = "monto_total")
    private BigDecimal montoTotal;

    @Column(name = "monto_ya_pagado")
    private BigDecimal montoYaPagado;

    @Column(name = "cantidad_cuotas")
    private Integer cantidadCuotas;

    @Column(name = "cantidad_cuotas_pagadas")
    private Integer cantidadCuotasPagadas;

    @Column(name = "dia_vencimiento")
    private Integer diaVencimiento;
}
