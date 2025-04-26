package com.franco.dev.domain.configuracion;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.enums.TipoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
//@Entity
//@Table(name = "notificacion_push")
public class NotificacionPush {
//    public class NotificacionPush implements Serializable {
//
//    private static final long serialVersionUID = 1L;
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "usuario_id", nullable = false)
    private Persona persona;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;
//
//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "tipo_cliente_id", nullable = false)
    private TipoCliente tipoCliente;

    private String titulo;
    private String mensaje;
    private String token;
    private String data;

//    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}



