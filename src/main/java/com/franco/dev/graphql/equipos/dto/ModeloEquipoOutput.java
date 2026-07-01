package com.franco.dev.graphql.equipos.dto;

import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModeloEquipoOutput {
    private Long id;
    private String descripcion;
    private MarcaEquipoOutput marca;
    private Usuario usuario;
    private LocalDateTime creadoEn;
}
