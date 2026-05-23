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
        Long usuarioId = marcacion.getUsuario().getId();

        Optional<Jornada> jornadaNocturnaAbierta = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                usuarioId, fechaReferencia);
        if (jornadaNocturnaAbierta.isPresent()) {
            return jornadaNocturnaAbierta.get();
        }

        Optional<Jornada> jornadaAbierta = jornadaService.findJornadaAbiertaConEntradaSinSalida(
                usuarioId, fechaReferencia);
        if (jornadaAbierta.isPresent()) {
            return jornadaAbierta.get();
        }

        log.warn("Salida rechazada: no hay jornada abierta para usuario {} en fecha {}",
                usuarioId, fechaReferencia);
        throw new IllegalStateException("No existe una jornada abierta para registrar la salida");
    }

    private Jornada resolverEntrada(Marcacion marcacion, LocalDate fechaReferencia) {
        Long usuarioId = marcacion.getUsuario().getId();

        Optional<Jornada> jornadaCruzaMedianoche = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                usuarioId, fechaReferencia);
        if (jornadaCruzaMedianoche.isPresent()) {
            Jornada abierta = jornadaCruzaMedianoche.get();
            if (abierta.getMarcacionEntrada() != null && abierta.getMarcacionSalida() == null) {
                return abierta;
            }
        }

        Optional<Jornada> jornadaAbiertaConEntrada = jornadaService.findJornadaAbiertaConEntradaSinSalida(
                usuarioId, fechaReferencia);
        if (jornadaAbiertaConEntrada.isPresent()) {
            return jornadaAbiertaConEntrada.get();
        }

        Optional<Jornada> jornadaAbiertaSinEntrada = jornadaService.findJornadaAbiertaSinEntrada(
                usuarioId, fechaReferencia);
        if (jornadaAbiertaSinEntrada.isPresent()) {
            return jornadaAbiertaSinEntrada.get();
        }

        List<Jornada> jornadas = jornadaService.findByUsuarioIdAndFecha(usuarioId, fechaReferencia.toString());

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
