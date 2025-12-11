package com.franco.dev.domain.configuraciones;

import com.franco.dev.config.Identifiable;
import com.franco.dev.domain.configuraciones.enums.TipoOperacion;
import com.franco.dev.domain.empresarial.Sucursal;
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
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@TypeDef(
        name = "tipo_operacion",
        typeClass = PostgreSQLEnumType.class
)
@Table(name = "modificacion_registro", schema = "configuraciones")
public class ModificacionRegistro implements Identifiable<Long> {

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

    @Column(name = "tipo_entidad", nullable = false, length = 100)
    private String tipoEntidad;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(name = "entidad_sucursal_id")
    private Long entidadSucursalId;

    @Column(name = "schema_nombre", nullable = false, length = 50)
    private String schemaNombre;

    @Column(name = "tabla_nombre", nullable = false, length = 100)
    private String tablaNombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacion", nullable = false, length = 20)
    @Type(type = "tipo_operacion")
    private TipoOperacion tipoOperacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sucursal_id", nullable = true)
    private Sucursal sucursal;

    @Column(name = "modificado_en", nullable = false)
    private LocalDateTime modificadoEn;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @OneToMany(mappedBy = "modificacionRegistro", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ModificacionDetalle> detalles;
}

