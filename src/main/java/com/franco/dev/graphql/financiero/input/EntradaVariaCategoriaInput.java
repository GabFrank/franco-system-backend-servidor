package com.franco.dev.graphql.financiero.input;

import lombok.Data;

@Data
public class EntradaVariaCategoriaInput {
    private Long id;
    private String nombre;
    private Long padreId;
    private String icono;
    private Boolean activo;
}
