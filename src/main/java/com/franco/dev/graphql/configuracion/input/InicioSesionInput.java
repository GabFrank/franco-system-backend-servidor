package com.franco.dev.graphql.configuracion.input;

import com.franco.dev.domain.configuracion.enums.TipoDispositivo;
import lombok.Data;

@Data
public class InicioSesionInput {
    private Long id;
    private Long usuarioId;
    private Long sucursalId;
    private TipoDispositivo tipoDespositivo;
    private String idDispositivo;
    private String token;
    private String horaInicio;
    private String horaFin;
    private String creadoEn;
}
