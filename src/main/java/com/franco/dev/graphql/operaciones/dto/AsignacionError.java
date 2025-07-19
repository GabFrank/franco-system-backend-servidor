package com.franco.dev.graphql.operaciones.dto;

import lombok.Data;

@Data
public class AsignacionError {
    private Long pedidoItemId;
    private String error;
} 