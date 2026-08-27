package com.franco.dev.domain.rrhh;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "penalizacion", schema = "rrhh")
public class Penalizacion implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(
            name = "assigned-identity",
            strategy = "com.franco.dev.config.AssignedIdentityGenerator"
    )
    @GeneratedValue(
            generator = "assigned-identity",
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id", nullable = true)
    private Funcionario funcionario;

    // FK plana a la jornada (PK compuesta): identifica la jornada que
    // origino una penalizacion auto-generada por tardanza.
    @Column(name = "jornada_id")
    private Long jornadaId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Enumerated(EnumType.STRING)
    private PenalizacionTipo tipo;

    private String descripcion;

    private BigDecimal monto;

    private LocalDate fecha;

    @Column(name = "auto_generada")
    private Boolean autoGenerada;

    private Boolean anulada;

    /**
     * Numero correlativo de advertencia del funcionario (1a, 2a, 3a...). Solo se llena
     * para tipo ADVERTENCIA; el resto de las penalizaciones lo dejan en null.
     */
    @Column(name = "numero_advertencia")
    private Integer numeroAdvertencia;

    /** Si el funcionario firmo el acta. Negarse a firmar tambien es un dato. */
    private Boolean firmada;

    /** Cuando ocurrio el hecho, si no coincide con el dia en que se registro. */
    @Column(name = "fecha_hecho")
    private LocalDate fechaHecho;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = true)
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
