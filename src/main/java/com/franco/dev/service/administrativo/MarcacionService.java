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
    private final com.franco.dev.repository.administrativo.HorarioRepository horarioRepository;

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
        if (e.getUsuario() == null && entity.getUsuario() != null) {
            e.setUsuario(entity.getUsuario());
        }

        procesarJornada(e);
        return e;
    }

    private void procesarJornada(Marcacion marcacion) {
        try {
            if (marcacion.getUsuario() == null || marcacion.getUsuario().getId() == null) {
                return;
            }

            LocalDateTime fechaReferencia = marcacion.getFechaEntrada();
            if (fechaReferencia == null)
                fechaReferencia = marcacion.getFechaSalida();
            if (fechaReferencia == null)
                fechaReferencia = LocalDateTime.now();

            int dayOfWeekValue = fechaReferencia.getDayOfWeek().getValue();
            if (dayOfWeekValue == 7)
                dayOfWeekValue = 0;
            com.franco.dev.domain.administrativo.enums.Dia diaSemana = com.franco.dev.domain.administrativo.enums.Dia
                    .values()[dayOfWeekValue];

            com.franco.dev.domain.administrativo.Horario horario = null;
            if (marcacion.getUsuario() != null && diaSemana != null) {
                horario = horarioRepository.findByUsuarioIdAndDia(marcacion.getUsuario().getId(), diaSemana);
            }

            if (horario == null) {
                com.franco.dev.domain.personas.Funcionario funcionario = funcionarioService
                        .findByUsuarioId(marcacion.getUsuario().getId());
                if (funcionario != null) {
                    horario = funcionario.getHorario();
                }
            }

            List<Jornada> jornadas = jornadaService.findByUsuarioIdAndFecha(
                    marcacion.getUsuario().getId(),
                    fechaReferencia.toLocalDate().toString());

            Jornada jornada = null;
            if (jornadas != null && !jornadas.isEmpty()) {
                Jornada lastJornada = jornadas.get(jornadas.size() - 1);
                boolean crearNueva = false;
                if (lastJornada.getMarcacionSalida() != null) {
                    crearNueva = true;
                } else if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.ENTRADA) {
                    boolean cabeEnActual = false;
                    if (lastJornada.getMarcacionEntrada() == null) {
                        cabeEnActual = true;
                    } else if (lastJornada.getMarcacionSalidaAlmuerzo() != null
                            && lastJornada.getMarcacionEntradaAlmuerzo() == null) {
                        cabeEnActual = true;
                    }
                    if (!cabeEnActual)
                        crearNueva = true;
                }

                if (crearNueva) {
                    jornada = new Jornada();
                    jornada.setUsuario(marcacion.getUsuario());
                    jornada.setFecha(fechaReferencia.toLocalDate());
                    jornada.setEstado(EstadoJornada.INCOMPLETO);
                } else {
                    jornada = lastJornada;
                }
            } else {
                jornada = new Jornada();
                jornada.setUsuario(marcacion.getUsuario());
                jornada.setFecha(fechaReferencia.toLocalDate());
                jornada.setEstado(EstadoJornada.INCOMPLETO);
            }

            if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.ENTRADA) {
                if (jornada.getMarcacionEntrada() == null) {
                    jornada.setMarcacionEntrada(marcacion);

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
                } else if (jornada.getMarcacionSalidaAlmuerzo() != null
                        && jornada.getMarcacionEntradaAlmuerzo() == null) {
                    jornada.setMarcacionEntradaAlmuerzo(marcacion);
                } else {
                }
            } else if (marcacion.getTipo() == com.franco.dev.domain.administrativo.enums.TipoMarcacion.SALIDA) {
                if (jornada.getMarcacionEntrada() != null && jornada.getMarcacionSalidaAlmuerzo() == null) {
                    jornada.setMarcacionSalidaAlmuerzo(marcacion);
                } else if (jornada.getMarcacionEntradaAlmuerzo() != null && jornada.getMarcacionSalida() == null) {
                    jornada.setMarcacionSalida(marcacion);
                    jornada.setEstado(EstadoJornada.NORMAL);
                }
            }

            long min1 = 0;
            if (jornada.getMarcacionEntrada() != null && jornada.getMarcacionSalidaAlmuerzo() != null) {
                LocalDateTime start = jornada.getMarcacionEntrada().getFechaEntrada();
                LocalDateTime end = jornada.getMarcacionSalidaAlmuerzo().getFechaSalida();
                if (end == null)
                    end = jornada.getMarcacionSalidaAlmuerzo().getFechaEntrada();

                if (start != null && end != null) {
                    min1 = ChronoUnit.MINUTES.between(start, end);
                }
            }

            long min2 = 0;
            if (jornada.getMarcacionEntradaAlmuerzo() != null && jornada.getMarcacionSalida() != null) {
                LocalDateTime start = jornada.getMarcacionEntradaAlmuerzo().getFechaEntrada();
                LocalDateTime end = jornada.getMarcacionSalida().getFechaSalida();
                if (end == null)
                    end = jornada.getMarcacionSalida().getFechaEntrada();

                if (start != null && end != null) {
                    min2 = ChronoUnit.MINUTES.between(start, end);
                }
            }

            jornada.setMinutosTrabajados(min1 + min2);
            jornadaService.save(jornada);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
