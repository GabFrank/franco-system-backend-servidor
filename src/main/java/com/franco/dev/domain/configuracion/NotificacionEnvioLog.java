package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.personas.Usuario;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion_envio_log", schema = "configuraciones")
public class NotificacionEnvioLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notificacion_id", nullable = false)
    private Notificacion notificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "token_fcm")
    private String tokenFcm;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio")
    private EstadoEnvio estadoEnvio;

    @Column(name = "mensaje_error")
    private String mensajeError;

    /**
     * Intentos de envio de esta fila. Es por destino y no por notificacion:
     * cada token agota su propio presupuesto de reintentos.
     */
    @Column(name = "intentos")
    private Integer intentos;
    
    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;
    
    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;
}

