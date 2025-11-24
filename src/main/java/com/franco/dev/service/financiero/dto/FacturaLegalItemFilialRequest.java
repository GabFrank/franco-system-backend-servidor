package com.franco.dev.service.financiero.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaLegalItemFilialRequest {
    private Long productoId;
    private String descripcion;
    private Double cantidad;
    private Double precioUnitario;
    private Double total;
    private Integer iva;
    private String unidadMedida;
}

