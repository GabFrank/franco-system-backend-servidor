package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.enums.ResultadoIncorporacionEmbedding;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncorporarEmbeddingMarcacionResult {
    private ResultadoIncorporacionEmbedding resultado;
    private String mensaje;
}
