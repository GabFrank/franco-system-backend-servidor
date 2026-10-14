package com.franco.dev.graphql.personas.input;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UsuarioInput {
    private Long id;
    private String password;
    private String nickname;
    private LocalDateTime creadoEn;
    private Long personaId;
    private Long usuarioId;
    private Boolean activo;
    /** Alta con la clave elegida. Un central sin este campo rechaza el input: no guarda mal. */
    private Boolean respetarClave;
}
