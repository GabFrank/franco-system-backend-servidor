package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreGastoStatusMetadataDTO {
    private String estado;
    private String etiqueta;
    private String icono;
    private String color;
}
