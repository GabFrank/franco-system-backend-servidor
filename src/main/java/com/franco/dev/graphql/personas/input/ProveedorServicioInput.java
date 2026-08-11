package com.franco.dev.graphql.personas.input;

import lombok.Data;

@Data
public class ProveedorServicioInput {
    private Long id;
    private Long personaId;
    private Long cuentaBancariaId;
    private String nombreContacto;
    private String numeroContacto;
    private Long usuarioId;
}
