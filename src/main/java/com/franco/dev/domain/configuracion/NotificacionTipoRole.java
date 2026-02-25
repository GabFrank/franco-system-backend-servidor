package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.personas.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion_tipo_role", schema = "configuraciones", uniqueConstraints = @UniqueConstraint(columnNames = {
        "role_id", "tipo_notificacion" }))
public class NotificacionTipoRole implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "tipo_notificacion", nullable = false)
    private String tipoNotificacion;

    private String descripcion;

    @Column(name = "es_obligatorio")
    private Boolean esObligatorio = false;

    @CreationTimestamp
    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}
