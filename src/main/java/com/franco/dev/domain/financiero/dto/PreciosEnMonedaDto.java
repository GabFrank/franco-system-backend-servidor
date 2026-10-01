package com.franco.dev.domain.financiero.dto;

import com.franco.dev.domain.financiero.Moneda;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Una lista de importes en guaranies, expresada en una moneda.
 *
 * `montos` va en el MISMO orden que los `montosGs` que se pidieron: el cliente los empareja por
 * posicion, sin volver a calcular nada. La conversion y el redondeo se hacen aca y no en el
 * cliente porque el dinero lo calcula el central (regla 6 de la PWA): la consulta de precios del
 * salon tiene que mostrar la misma cifra que cobraria la caja.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreciosEnMonedaDto {
    private Moneda moneda;
    /** Con cuantos decimales se redondeo, para que el cliente formatee igual. */
    private Integer decimales;
    private List<Double> montos;
}
