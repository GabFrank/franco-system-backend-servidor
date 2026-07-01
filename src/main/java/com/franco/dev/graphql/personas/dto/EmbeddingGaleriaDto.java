package com.franco.dev.graphql.personas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingGaleriaDto {
    private List<Double> master = new ArrayList<>();
    private List<EmbeddingGaleriaItemDto> gallery = new ArrayList<>();
}
