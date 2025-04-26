package com.franco.dev.graphql.personas.input;

import com.franco.dev.domain.personas.enums.TipoCliente;
import lombok.Data;

@Data
public class ClienteInput {
    private Long id;
    private TipoCliente tipo;
    private Float credito;
    private String codigo;
    private Long sucursalId;
    private Long personaId;
    private Long usuarioId;
    private String direccion;
    private String nombre;
    private Boolean tributa;
    private Boolean verificadoSet;
    private String documento;
}
