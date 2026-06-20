package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.EstadoMarcacionUsuario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.service.administrativo.helper.JornadaMarcacionRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstadoMarcacionService {

    private final JornadaService jornadaService;
    private final JornadaMarcacionRules jornadaMarcacionRules;

    public EstadoMarcacionUsuario obtenerEstado(Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);
        List<Jornada> jornadas = jornadaService.findByUsuarioIdAndFechaRange(
                usuarioId, ayer.toString(), hoy.toString());
        return jornadaMarcacionRules.construirEstado(jornadas, hoy);
    }
}
