package com.franco.dev.domain.administrativo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Totales de las jornadas que cumplen un filtro del historial, sin importar la pagina. */
@Data
@AllArgsConstructor
public class JornadaResumen {
    private Integer diasTrabajados;
    private Integer minutosTrabajados;
    private Integer minutosExtras;
    private Integer minutosLlegadaTardia;
}
