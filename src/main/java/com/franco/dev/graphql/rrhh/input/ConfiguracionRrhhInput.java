package com.franco.dev.graphql.rrhh.input;

import com.franco.dev.domain.rrhh.enums.ConfiguracionRrhhTipo;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfiguracionRrhhInput {
    private Long id;
    private String clave;
    private String valor;
    private ConfiguracionRrhhTipo tipo;
    private String descripcion;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private Long usuarioId;
}
