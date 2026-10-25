package com.franco.dev.domain.personas;

import com.franco.dev.domain.personas.enums.EstadoRecuperacionContrasena;
import com.franco.dev.domain.personas.enums.TipoRecuperacionContrasena;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Un pedido de "¿Olvido su contrasena?". Central-only (V250.3). La logica vive en
 * {@code RecuperacionContrasenaService}.
 *
 * <p>{@code codigoHash} es el BCrypt del codigo que prueba al usuario: el de 6 digitos que llego
 * al correo (CORREO) o el secreto que vio en pantalla al pedir la aprobacion de RRHH (RRHH). No
 * esta en el schema GraphQL: nunca sale del central.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "recuperacion_contrasena", schema = "personas")
public class RecuperacionContrasena implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    private TipoRecuperacionContrasena tipo;

    @Enumerated(EnumType.STRING)
    private EstadoRecuperacionContrasena estado;

    private String email;
    private String telefono;
    private String observacion;

    @ToString.Exclude
    @Column(name = "codigo_hash")
    private String codigoHash;

    private Integer intentos = 0;
    private String ip;

    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resuelto_por_id")
    private Usuario resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    /** Paso el plazo: para aprobarla (PENDIENTE de RRHH) o para usarla (CORREO o APROBADA). */
    public Boolean getVencida() {
        return venceEn != null && LocalDateTime.now().isAfter(venceEn)
                && (estado == EstadoRecuperacionContrasena.PENDIENTE
                    || estado == EstadoRecuperacionContrasena.APROBADA);
    }
}
