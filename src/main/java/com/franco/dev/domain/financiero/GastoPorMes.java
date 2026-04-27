package com.franco.dev.domain.financiero;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GastoPorMes {
    private Integer mes;
    private Double total;
    private Long cantidad;
}
