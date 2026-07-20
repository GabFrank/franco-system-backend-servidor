package com.franco.dev.graphql.equipos.dto;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EquipoOutput {
    private Long id;
    private Persona propietario;
    private String identificador;
    private ModeloEquipoOutput modelo;
    private String descripcion;
    private String imagenes;
    private TipoEquipoOutput tipoEquipo;
    private Boolean consumeEnergia;
    private String consumoValor;
    private EquipoFinancieroOutput financiero;
    private Sucursal sucursal;
    private Usuario usuario;
    private LocalDateTime creadoEn;
}
