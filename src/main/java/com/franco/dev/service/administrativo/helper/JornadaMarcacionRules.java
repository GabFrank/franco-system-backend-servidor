package com.franco.dev.service.administrativo.helper;

import com.franco.dev.domain.administrativo.EstadoMarcacionUsuario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.enums.AccionMarcacionPendiente;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import com.franco.dev.domain.administrativo.enums.Turno;
import com.franco.dev.service.administrativo.JornadaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JornadaMarcacionRules {

    private final JornadaService jornadaService;

    /**
     * Turnos NOCHE/MADRUGADA o horarios cuya salida es anterior a la entrada (cruzan medianoche).
     */
    public boolean cruzaMedianoche(Jornada jornada) {
        if (jornada == null) {
            return false;
        }
        if (jornada.getTurno() == Turno.NOCHE || jornada.getTurno() == Turno.MADRUGADA) {
            return true;
        }
        if (jornada.getHoraEntradaHorario() != null && jornada.getHoraSalidaHorario() != null) {
            return jornada.getHoraSalidaHorario().isBefore(jornada.getHoraEntradaHorario());
        }
        return false;
    }

    /**
     * Jornadas de turno DIA vencen a medianoche. NOCHE/MADRUGADA permanecen activas al día siguiente
     * mientras no tengan salida definitiva.
     */
    public boolean esJornadaActiva(Jornada jornada, LocalDate fechaConsulta) {
        if (jornada == null
                || jornada.getEstado() != EstadoJornada.INCOMPLETO
                || jornada.getMarcacionSalida() != null) {
            return false;
        }
        if (!jornada.getFecha().isBefore(fechaConsulta)) {
            return true;
        }
        return cruzaMedianoche(jornada) && jornada.getMarcacionEntrada() != null;
    }

    public boolean esRetornoAlmuerzoPendiente(Jornada jornada) {
        return jornada != null
                && jornada.getMarcacionEntrada() != null
                && jornada.getMarcacionSalidaAlmuerzo() != null
                && jornada.getMarcacionEntradaAlmuerzo() == null;
    }

    public boolean esJornadaAbiertaSinSalida(Jornada jornada) {
        return jornada != null
                && jornada.getEstado() == EstadoJornada.INCOMPLETO
                && jornada.getMarcacionEntrada() != null
                && jornada.getMarcacionSalida() == null;
    }

    public void validarEntrada(Long usuarioId, LocalDate fechaMarcacion) {
        Optional<Jornada> jornadaHoy = jornadaService.findJornadaAbiertaConEntradaSinSalida(usuarioId, fechaMarcacion);
        if (jornadaHoy.isPresent()) {
            if (esRetornoAlmuerzoPendiente(jornadaHoy.get())) {
                return;
            }
            throw new IllegalStateException(
                    "Ya registró entrada en la jornada del día. Debe marcar salida antes de una nueva entrada.");
        }

        Optional<Jornada> jornadaNocturna = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                usuarioId, fechaMarcacion);
        if (jornadaNocturna.isPresent()) {
            Jornada jornada = jornadaNocturna.get();
            if (jornada.getFecha().isBefore(fechaMarcacion)) {
                if (esRetornoAlmuerzoPendiente(jornada)) {
                    return;
                }
                if (jornada.getMarcacionEntrada() != null) {
                    throw new IllegalStateException(
                            "Tiene una jornada nocturna o madrugada abierta. "
                                    + "Debe marcar salida antes de registrar una nueva entrada.");
                }
            }
        }
    }

    public void validarSalida(Long usuarioId, LocalDate fechaMarcacion) {
        Optional<Jornada> jornadaNocturna = jornadaService.findIncompletaSinSalidaNocturnaParaSalida(
                usuarioId, fechaMarcacion);
        if (jornadaNocturna.isPresent()) {
            return;
        }

        Optional<Jornada> jornadaAbierta = jornadaService.findJornadaAbiertaConEntradaSinSalida(
                usuarioId, fechaMarcacion);
        if (jornadaAbierta.isPresent()) {
            return;
        }

        throw new IllegalStateException("No existe una jornada abierta para registrar la salida");
    }

    public AccionMarcacionPendiente resolverAccionPendiente(Jornada jornada, LocalDate fechaConsulta) {
        if (jornada == null
                || !esJornadaActiva(jornada, fechaConsulta)
                || jornada.getEstado() == EstadoJornada.NORMAL
                || jornada.getMarcacionSalida() != null) {
            return AccionMarcacionPendiente.ENTRADA;
        }
        if (jornada.getMarcacionEntrada() == null) {
            return AccionMarcacionPendiente.ENTRADA;
        }
        if (jornada.getMarcacionSalidaAlmuerzo() == null) {
            return AccionMarcacionPendiente.SALIDA;
        }
        if (jornada.getMarcacionEntradaAlmuerzo() == null) {
            return AccionMarcacionPendiente.RETORNO_ALMUERZO;
        }
        return AccionMarcacionPendiente.SALIDA_DEFINITIVA;
    }

    public Jornada seleccionarJornadaRelevante(List<Jornada> jornadas, LocalDate fechaConsulta) {
        if (jornadas == null || jornadas.isEmpty()) {
            return null;
        }

        List<Jornada> abiertas = jornadas.stream()
                .filter(this::esJornadaAbiertaSinSalida)
                .collect(Collectors.toList());

        if (!abiertas.isEmpty()) {
            List<Jornada> nocturnas = abiertas.stream()
                    .filter(this::cruzaMedianoche)
                    .collect(Collectors.toList());
            List<Jornada> candidatas = nocturnas.isEmpty() ? abiertas : nocturnas;
            return candidatas.stream()
                    .max(Comparator.comparing(Jornada::getId))
                    .orElse(null);
        }

        return jornadas.stream()
                .max(Comparator
                        .comparing((Jornada j) -> j.getFecha().equals(fechaConsulta))
                        .thenComparing(Jornada::getId))
                .orElse(null);
    }

    public EstadoMarcacionUsuario construirEstado(List<Jornada> jornadas, LocalDate fechaConsulta) {
        Jornada relevante = seleccionarJornadaRelevante(jornadas, fechaConsulta);
        AccionMarcacionPendiente accion = resolverAccionPendiente(relevante, fechaConsulta);
        boolean activa = relevante != null && esJornadaActiva(relevante, fechaConsulta);

        EstadoMarcacionUsuario estado = new EstadoMarcacionUsuario();
        estado.setJornadaRelevante(relevante);
        estado.setAccionPendiente(accion);
        estado.setEstaEnJornada(activa && accion != AccionMarcacionPendiente.ENTRADA);
        estado.setPuedeMarcarEntrada(accion == AccionMarcacionPendiente.ENTRADA);
        estado.setPuedeMarcarSalida(
                accion == AccionMarcacionPendiente.SALIDA
                        || accion == AccionMarcacionPendiente.SALIDA_DEFINITIVA);
        estado.setPuedeMarcarSalidaAlmuerzo(accion == AccionMarcacionPendiente.SALIDA);
        estado.setPuedeMarcarEntradaAlmuerzo(accion == AccionMarcacionPendiente.RETORNO_ALMUERZO);
        return estado;
    }
}
