package com.franco.dev.domain.administrativo.dto;

import com.franco.dev.domain.administrativo.Jornada;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

/** Una pagina del historial de marcaciones junto con el resumen de todo el filtro. */
@Data
@AllArgsConstructor
public class JornadaHistorial {
    private Page<Jornada> pagina;
    private JornadaResumen resumen;
}
