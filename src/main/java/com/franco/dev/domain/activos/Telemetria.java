package com.franco.dev.domain.activos;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "telemetria", schema = "vehiculos")
public class Telemetria implements Identifiable<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GenericGenerator(name = "assigned-identity", strategy = "com.franco.dev.config.AssignedIdentityGenerator")
    @GeneratedValue(generator = "assigned-identity", strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    private Gps dispositivo;

    @Column(name = "fecha_servidor")
    private LocalDateTime fechaServidor;

    @Column(name = "fecha_gps")
    private LocalDateTime fechaGps;

    private Double latitud;

    private Double longitud;

    private Integer velocidad;

    private Integer direccion;

    private Boolean ignicion;

    private String alarma;

    @Column(name = "json_data", columnDefinition = "jsonb")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonBinaryType")
    private String jsonData;

    @PrePersist
    public void prePersist() {
        this.fechaServidor = LocalDateTime.now();
    }
}
