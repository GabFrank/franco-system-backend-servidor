package com.franco.dev.domain.activos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "vehiculo_especificaciones", schema = "vehiculos")
public class VehiculoEspecificaciones {

    @Id
    @Column(name = "vehiculo_id")
    private Long vehiculoId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "primer_kilometraje")
    private BigDecimal primerKilometraje;

    @Column(name = "capacidad_kg")
    private BigDecimal capacidadKg;

    @Column(name = "capacidad_pasajeros")
    private Integer capacidadPasajeros;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_combustible_id")
    private TipoCombustible tipoCombustible;

    private String chasis;

    @Column(name = "aire_acondicionado")
    private Boolean aireAcondicionado;

    @Column(name = "mantenimiento_motor_intervalo")
    private Integer mantenimientoMotorIntervalo;

    @Column(name = "mantenimiento_caja_intervalo")
    private Integer mantenimientoCajaIntervalo;
}
