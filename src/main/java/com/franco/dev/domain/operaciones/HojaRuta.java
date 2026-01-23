package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.vehiculos.Vehiculo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "hoja_ruta", schema = "operaciones")
public class HojaRuta implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chofer_id", nullable = false)
    private Persona chofer;

    @Column(name = "fecha_salida")
    private LocalDateTime fechaSalida;

    @Column(name = "fecha_llegada")
    private LocalDateTime fechaLlegada;

    @Column(name = "km_salida")
    private BigDecimal kmSalida;

    @Column(name = "km_llegada")
    private BigDecimal kmLlegada;

    private String estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "hoja_ruta_acompanante", schema = "operaciones", joinColumns = @JoinColumn(name = "hoja_ruta_id"), inverseJoinColumns = @JoinColumn(name = "persona_id"))
    private List<Persona> acompanantes;

    @PrePersist
    public void prePersist() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "EN_RUTA";
        }
    }
}
