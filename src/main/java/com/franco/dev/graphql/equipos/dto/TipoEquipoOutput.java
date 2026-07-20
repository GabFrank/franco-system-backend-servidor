package com.franco.dev.graphql.equipos.dto;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TipoEquipoOutput {
    private Long id;
    private String descripcion;
    private Sucursal sucursal;
    private Usuario usuario;
    private LocalDateTime creadoEn;
}
