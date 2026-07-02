package com.franco.dev.graphql.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoVencimientoCompraProjectionDTO {

    private Long productoId;
    private LocalDate vencimientoEnNota;
}
