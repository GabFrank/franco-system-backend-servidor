package com.franco.dev.domain.operaciones;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VentaPorMes {
    private Integer mes;
    private Double total;
    private Long cantidad;
}
