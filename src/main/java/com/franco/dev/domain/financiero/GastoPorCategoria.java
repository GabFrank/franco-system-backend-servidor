package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GastoPorCategoria {
    private String categoria;
    private Double total;
    private Long cantidad;
}
