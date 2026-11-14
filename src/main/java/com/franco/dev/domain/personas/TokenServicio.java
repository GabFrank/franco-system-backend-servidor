package com.franco.dev.domain.personas;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Credencial de un bot para entrar al GraphQL (issue #579). Se guarda solo el SHA-256 del token;
 * el token en claro se devuelve una sola vez, al emitirlo. Central-only (V267.5).
 *
 * La validacion de cada request no pasa por esta entidad: la hace {@code TokenServicioValidator}
 * por JDBC, dentro del filtro de seguridad.
 */
@Getter
@Setter
@Entity
@Table(name = "token_servicio", schema = "personas")
public class TokenServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private String nombre;

    /** Los primeros caracteres del token, para reconocerlo en listados y logs. */
    private String prefijo;

    /** SHA-256 en hex del token. Nunca sale por GraphQL. */
    private String hash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "revocado_por")
    private Usuario revocadoPor;

    @Column(name = "ultimo_uso_en", insertable = false, updatable = false)
    private LocalDateTime ultimoUsoEn;

    /**
     * Sin revocar y sin vencer. No mira el usuario: un token vigente de un usuario inactivo o
     * superusuario igual se rechaza al validarlo.
     */
    @Transient
    public boolean getVigente() {
        return revocadoEn == null && expiraEn != null && expiraEn.isAfter(LocalDateTime.now());
    }
}
