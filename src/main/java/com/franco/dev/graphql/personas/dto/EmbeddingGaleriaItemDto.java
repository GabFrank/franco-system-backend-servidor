package com.franco.dev.graphql.personas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingGaleriaItemDto {
    private String pose;
    private List<Double> embedding;
    private Double score;
}
