package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.service.administrativo.JornadaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JornadaMarcacionResolver {

    private static final Logger log = LoggerFactory.getLogger(JornadaMarcacionResolver.class);

    private final JornadaService jornadaService;
    private final JornadaFactory jornadaFactory;

    public Jornada resolver(Marcacion marcacion, LocalDate fechaReferencia) {
        if (marcacion.getTipo() == TipoMarcacion.SALIDA) {
            return resolverSalida(marcacion, fechaReferencia);
        }

        return resolverEntrada(marcacion, fechaReferencia);
    }

    private Jornada resolverSalida(Marcacion marcacion, LocalDate fechaReferencia) {
        Optional<Jornada> jornadaNocturnaAbierta = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                marcacion.getUsuario().getId(), fechaReferencia);
        if (jornadaNocturnaAbierta.isPresent()) {
            return jornadaNocturnaAbierta.get();
        }

        List<Jornada> jornadasHoy = jornadaService.findByUsuarioIdAndFecha(
                marcacion.getUsuario().getId(),
                fechaReferencia.toString());

        if (jornadasHoy != null && !jornadasHoy.isEmpty()) {
            Jornada lastJornada = jornadasHoy.get(jornadasHoy.size() - 1);
            if (!shouldCreateNewJornada(lastJornada, marcacion)) {
                return lastJornada;
            }
        }

        log.warn("Salida rechazada: no hay jornada abierta para usuario {} en fecha {}",
                marcacion.getUsuario().getId(), fechaReferencia);
        throw new IllegalStateException("No existe una jornada abierta para registrar la salida");
    }

    private Jornada resolverEntrada(Marcacion marcacion, LocalDate fechaReferencia) {
        Optional<Jornada> jornadaCruzaMedianoche = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                marcacion.getUsuario().getId(), fechaReferencia);
        if (jornadaCruzaMedianoche.isPresent()) {
            Jornada abierta = jornadaCruzaMedianoche.get();
            if (abierta.getMarcacionEntrada() != null && abierta.getMarcacionSalida() == null) {
                return abierta;
            }
        }

        List<Jornada> jornadas = jornadaService.findByUsuarioIdAndFecha(
                marcacion.getUsuario().getId(),
                fechaReferencia.toString());

        if (jornadas == null || jornadas.isEmpty()) {
            return jornadaFactory.crearNuevaJornada(marcacion, fechaReferencia);
        }

        Jornada lastJornada = jornadas.get(jornadas.size() - 1);

        if (shouldCreateNewJornada(lastJornada, marcacion)) {
            return jornadaFactory.crearNuevaJornada(marcacion, fechaReferencia);
        }

        return lastJornada;
    }

    private boolean shouldCreateNewJornada(Jornada lastJornada, Marcacion marcacion) {
        if (lastJornada.getEstado() == EstadoJornada.NORMAL || lastJornada.getMarcacionSalida() != null) {
            return marcacion.getTipo() == TipoMarcacion.ENTRADA || marcacion.getFechaEntrada() != null;
        }
        return false;
    }
}
