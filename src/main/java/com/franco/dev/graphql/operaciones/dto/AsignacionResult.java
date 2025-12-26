package com.franco.dev.graphql.operaciones.dto;

import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import lombok.Data;

import java.util.List;

@Data
public class AsignacionResult {
    private Boolean success;
    private String message;
    private List<NotaRecepcionItem> notaRecepcionItems;
    private List<AsignacionError> errores;
} 