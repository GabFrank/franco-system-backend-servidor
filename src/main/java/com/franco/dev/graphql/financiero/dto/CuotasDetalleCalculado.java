package com.franco.dev.graphql.financiero.dto;

import com.franco.dev.graphql.financiero.input.CuotaDetalleInput;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuotasDetalleCalculado {
    private List<CuotaDetalleInput> cuotas;
    private BigDecimal montoTotal;
}
