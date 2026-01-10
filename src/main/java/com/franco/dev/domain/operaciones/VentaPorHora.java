package com.franco.dev.domain.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaPorHora {
    private Integer hora;
    private Double total;
    private Long cantidad;
}
