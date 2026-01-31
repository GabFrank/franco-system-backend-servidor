package com.franco.dev.domain.vehiculos;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "dispositivo_gps", schema = "vehiculos")
public class Gps implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String imei;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "modelo_tracker")
    private String modeloTracker;

    @Column(name = "sim_numero")
    private String simNumero;

    private Boolean activo;

    @Column(name = "creado_em")
    private LocalDateTime creadoEm;

    @Column(name = "ultima_latitud")
    private Double ultimaLatitud;

    @Column(name = "ultima_longitud")
    private Double ultimaLongitud;

    @Column(name = "ultima_fecha_reporte")
    private LocalDateTime ultimaFechaReporte;

    @Column(name = "ultima_ignicion")
    private Boolean ultimaIgnicion;

    @Column(name = "ultima_velocidad")
    private Integer ultimaVelocidad;

    @PrePersist
    public void prePersist() {
        this.creadoEm = LocalDateTime.now();
        if (this.activo == null) {
            this.activo = true;
        }
    }
}
