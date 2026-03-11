package com.franco.dev.domain.operaciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaEstado;
import com.franco.dev.domain.operaciones.enums.ProcesoEtapaTipo;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.utilitarios.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "proceso_etapa_tipo",
        typeClass = PostgreSQLEnumType.class
)
@TypeDef(
        name = "proceso_etapa_estado",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "proceso_etapa", schema = "operaciones")
public class ProcesoEtapa implements Identifiable<Long> {

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
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_etapa", nullable = false)
    @Type(type = "proceso_etapa_tipo")
    private ProcesoEtapaTipo tipoEtapa;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_etapa", nullable = false)
    @Type(type = "proceso_etapa_estado")
    private ProcesoEtapaEstado estadoEtapa;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_inicio_id")
    private Usuario usuarioInicio;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
} 