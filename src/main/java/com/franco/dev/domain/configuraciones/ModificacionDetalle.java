package com.franco.dev.domain.configuraciones;

import com.franco.dev.config.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "modificacion_detalle", schema = "configuraciones")
public class ModificacionDetalle implements Identifiable<Long> {

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
    @JoinColumn(name = "modificacion_registro_id", nullable = false)
    private ModificacionRegistro modificacionRegistro;

    @Column(name = "campo_nombre", nullable = false, length = 100)
    private String campoNombre;

    @Column(name = "campo_tipo", length = 50)
    private String campoTipo;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(name = "valor_anterior_id")
    private Long valorAnteriorId;

    @Column(name = "valor_nuevo_id")
    private Long valorNuevoId;

    @Column(name = "orden", nullable = false)
    private Integer orden = 1;

    @Column(name = "es_campo_sensible", nullable = false)
    private Boolean esCampoSensible = false;
}

