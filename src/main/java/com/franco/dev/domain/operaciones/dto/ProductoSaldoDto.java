package com.franco.dev.domain.operaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoSaldoDto {
    private Long productoId;
    private String productoDescripcion;
    private Long sucursalId;
    private Double saldoTotal;
} 