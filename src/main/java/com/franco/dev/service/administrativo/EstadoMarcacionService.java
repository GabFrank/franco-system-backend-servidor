package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.EstadoMarcacionUsuario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.service.administrativo.helper.JornadaMarcacionRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EstadoMarcacionService {

    private final JornadaService jornadaService;
    private final JornadaMarcacionRules jornadaMarcacionRules;

    public EstadoMarcacionUsuario obtenerEstado(Long usuarioId) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        LocalDate ayer = hoy.minusDays(1);
        List<Jornada> encontradas = jornadaService.findByUsuarioIdAndFechaRange(
                usuarioId, ayer.toString(), hoy.toString());
        List<Jornada> jornadas = encontradas != null ? new ArrayList<>(encontradas) : new ArrayList<>();

        // Una nocturna abierta de antes de ayer (el funcionario no marco la salida y al dia
        // siguiente no vino) quedaba fuera de la ventana: la app le ofrecia "Entrada" y el
        // central se la rechazaba por esa misma nocturna, sin salida posible. Se suma para
        // que la app ofrezca la salida.
        jornadaService.findIncompletaSinSalidaNocturnaParaSalida(usuarioId, hoy)
                .filter(nocturna -> jornadas.stream().noneMatch(j -> mismaJornada(j, nocturna)))
                .ifPresent(jornadas::add);

        return jornadaMarcacionRules.construirEstado(jornadas, hoy, ahora);
    }

    private boolean mismaJornada(Jornada a, Jornada b) {
        return Objects.equals(a.getId(), b.getId()) && Objects.equals(a.getSucursalId(), b.getSucursalId());
    }
}
