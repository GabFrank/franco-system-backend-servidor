package com.franco.dev.service.administrativo;

import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.enums.EstadoJornada;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository, Long> {

    private final MarcacionRepository repository;
    private final JornadaService jornadaService;
    private final com.franco.dev.service.personas.FuncionarioService funcionarioService;

    @Override
    public MarcacionRepository getRepository() {
        return repository;
    }

    public Page<Marcacion> findAllPaged(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAll(pageable);
    }

    public Page<Marcacion> findByUsuarioId(Long usuarioId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUsuarioId(usuarioId, pageable);
    }

    public Page<Marcacion> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin,
            Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, pageable);
    }

    public Page<Marcacion> findByFechaRange(String fechaInicio, String fechaFin, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByFechaRange(fechaInicio, fechaFin, pageable);
    }

    public List<Marcacion> findBySucursalEntradaId(Long sucursalId) {
        return repository.findBySucursalEntradaId(sucursalId);
    }

    public List<Marcacion> findBySucursalSalidaId(Long sucursalId) {
        return repository.findBySucursalSalidaId(sucursalId);
    }

    @Override
    public Marcacion save(Marcacion entity) {
        if (entity.getId() == null) {
            if (entity.getFechaEntrada() == null && entity.getFechaSalida() == null) {
                entity.setFechaEntrada(LocalDateTime.now());
            }
        }
        Marcacion e = super.save(entity);
        procesarJornada(e);
        return e;
    }

    private void procesarJornada(Marcacion marcacion) {
        try {
            com.franco.dev.domain.personas.Funcionario funcionario = funcionarioService
                    .findByUsuarioId(marcacion.getUsuario().getId());
            com.franco.dev.domain.administrativo.Horario horario = null;
            if (funcionario != null) {
                horario = funcionario.getHorario();
            }

            if (marcacion.getFechaEntrada() != null && marcacion.getFechaSalida() == null) {
                Optional<Jornada> existingJornadaFecha = jornadaService.findByUsuarioIdAndFecha(
                        marcacion.getUsuario().getId(),
                        marcacion.getFechaEntrada().toLocalDate().toString());

                if (!existingJornadaFecha.isPresent()) {
                    Jornada jornada = new Jornada();
                    jornada.setUsuario(marcacion.getUsuario());
                    jornada.setFecha(marcacion.getFechaEntrada().toLocalDate());
                    jornada.setMarcacionEntrada(marcacion);
                    jornada.setEstado(EstadoJornada.INCOMPLETO);
                    if (horario != null && horario.getHoraEntrada() != null) {
                        java.time.LocalTime horaEntradaReal = marcacion.getFechaEntrada().toLocalTime();
                        java.time.LocalTime horaEntradaHorario = horario.getHoraEntrada();
                        long minutosTolerancia = horario.getToleranciaMinutos() != null ? horario.getToleranciaMinutos()
                                : 0;
                        if (horaEntradaReal.isAfter(horaEntradaHorario.plusMinutes(minutosTolerancia))) {
                            long minutosTarde = ChronoUnit.MINUTES.between(horaEntradaHorario, horaEntradaReal);
                            jornada.setMinutosLlegadaTardia(minutosTarde);
                        }
                    }

                    jornadaService.save(jornada);
                } else {
                }

            } else if (marcacion.getFechaEntrada() != null && marcacion.getFechaSalida() != null) {
                Optional<Jornada> jornadaOpt = jornadaService.findByMarcacionEntradaId(marcacion.getId());
                if (!jornadaOpt.isPresent()) {
                    jornadaOpt = jornadaService.findByUsuarioIdAndFecha(
                            marcacion.getUsuario().getId(),
                            marcacion.getFechaEntrada().toLocalDate().toString());
                }

                if (jornadaOpt.isPresent()) {
                    Jornada jornada = jornadaOpt.get();
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);

                    if (jornada.getMarcacionEntrada() == null) {
                        jornada.setMarcacionEntrada(marcacion);
                    }

                    long minutos = ChronoUnit.MINUTES.between(
                            marcacion.getFechaEntrada(),
                            marcacion.getFechaSalida());
                    jornada.setMinutosTrabajados(minutos);
                    if (horario != null && horario.getHoraSalida() != null) {
                        java.time.LocalTime horaSalidaReal = marcacion.getFechaSalida().toLocalTime();
                        java.time.LocalTime horaSalidaHorario = horario.getHoraSalida();
                        if (horaSalidaReal.isAfter(horaSalidaHorario)) {
                            long minutosExtras = ChronoUnit.MINUTES.between(horaSalidaHorario, horaSalidaReal);
                            jornada.setMinutosExtras(minutosExtras);
                        }
                    }

                    jornadaService.save(jornada);
                } else {
                    Jornada jornada = new Jornada();
                    jornada.setUsuario(marcacion.getUsuario());
                    jornada.setFecha(marcacion.getFechaEntrada().toLocalDate());
                    jornada.setMarcacionEntrada(marcacion);
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);
                    long minutos = ChronoUnit.MINUTES.between(
                            marcacion.getFechaEntrada(),
                            marcacion.getFechaSalida());
                    jornada.setMinutosTrabajados(minutos);
                    if (horario != null && horario.getHoraEntrada() != null) {
                        java.time.LocalTime horaEntradaReal = marcacion.getFechaEntrada().toLocalTime();
                        java.time.LocalTime horaEntradaHorario = horario.getHoraEntrada();
                        long minutosTolerancia = horario.getToleranciaMinutos() != null ? horario.getToleranciaMinutos()
                                : 0;
                        if (horaEntradaReal.isAfter(horaEntradaHorario.plusMinutes(minutosTolerancia))) {
                            long minutosTarde = ChronoUnit.MINUTES.between(horaEntradaHorario, horaEntradaReal);
                            jornada.setMinutosLlegadaTardia(minutosTarde);
                        }
                    }
                    if (horario != null && horario.getHoraSalida() != null) {
                        java.time.LocalTime horaSalidaReal = marcacion.getFechaSalida().toLocalTime();
                        java.time.LocalTime horaSalidaHorario = horario.getHoraSalida();
                        if (horaSalidaReal.isAfter(horaSalidaHorario)) {
                            long minutosExtras = ChronoUnit.MINUTES.between(horaSalidaHorario, horaSalidaReal);
                            jornada.setMinutosExtras(minutosExtras);
                        }
                    }

                    jornadaService.save(jornada);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
