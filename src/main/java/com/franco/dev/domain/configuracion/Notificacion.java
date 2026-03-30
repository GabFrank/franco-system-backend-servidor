package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.configuracion.enums.EstadoNotificacion;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacionTablero;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion", schema = "configuraciones")
public class Notificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String mensaje;
    private String tipo;
    private String data;

    @Enumerated(EnumType.STRING)
    private EstadoNotificacion estado;

    // Estado compartido entre todos los usuarios destinatarios
    @Enumerated(EnumType.STRING)
    private EstadoNotificacionTablero estadoTablero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificado_por_usuario_id")
    private Usuario verificadoPorUsuario;

    private LocalDateTime fechaVerificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_creador_id")
    private Usuario usuarioCreador;

    private Integer intentosEnvio;
    private String ultimoError;

    @CreationTimestamp
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    private LocalDateTime actualizadoEn;

}