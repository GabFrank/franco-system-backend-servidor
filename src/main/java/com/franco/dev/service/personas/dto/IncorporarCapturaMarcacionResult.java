package com.franco.dev.service.personas.dto;

import com.franco.dev.domain.personas.enums.ResultadoIncorporacionEmbedding;
import com.franco.dev.graphql.personas.dto.EmbeddingGaleriaDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncorporarCapturaMarcacionResult {
    private ResultadoIncorporacionEmbedding resultado;
    private EmbeddingGaleriaDto galeria;
    private String mensaje;

    public static IncorporarCapturaMarcacionResult ok(EmbeddingGaleriaDto galeria) {
        return new IncorporarCapturaMarcacionResult(
                ResultadoIncorporacionEmbedding.OK,
                galeria,
                "Perfil facial actualizado con esta marcación.");
    }

    public static IncorporarCapturaMarcacionResult rechazadoScore(String mensaje) {
        return new IncorporarCapturaMarcacionResult(
                ResultadoIncorporacionEmbedding.RECHAZADO_SCORE,
                null,
                mensaje);
    }

    public static IncorporarCapturaMarcacionResult rechazadoSimilitud(String mensaje) {
        return new IncorporarCapturaMarcacionResult(
                ResultadoIncorporacionEmbedding.RECHAZADO_SIMILITUD,
                null,
                mensaje);
    }
}
