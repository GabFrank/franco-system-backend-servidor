package com.franco.dev.service.administrativo;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.administrativo.Horario;
import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.administrativo.Marcacion;
import com.franco.dev.domain.administrativo.enums.TipoMarcacion;
import com.franco.dev.repository.administrativo.MarcacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.administrativo.helper.*;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MarcacionService extends CrudService<Marcacion, MarcacionRepository, EmbebedPrimaryKey> {

    private static final Logger log = LoggerFactory.getLogger(MarcacionService.class);

    private final MarcacionRepository repository;
    private final JornadaService jornadaService;
    private final HorarioResolver horarioResolver;
    private final JornadaMarcacionResolver jornadaMarcacionResolver;
    private final TardanzaCalculator tardanzaCalculator;
    private final HorasTrabajadasCalculator horasTrabajadasCalculator;
    private final AlmuerzoProcessor almuerzoProcessor;

    @Override
    public MarcacionRepository getRepository() {
        return repository;
    }

    public Page<Marcacion> findAllPaged(Integer page, Integer size) {
        return repository.findAll(PageRequest.of(page, size));
    }

    public Page<Marcacion> findByUsuarioId(Long usuarioId, Integer page, Integer size) {
        return repository.findByUsuarioId(usuarioId, PageRequest.of(page, size));
    }

    public Page<Marcacion> findByUsuarioIdAndFechaRange(Long usuarioId, String fechaInicio, String fechaFin,
            Integer page, Integer size) {
        return repository.findByUsuarioIdAndFechaRange(usuarioId, fechaInicio, fechaFin, PageRequest.of(page, size));
    }

    public Page<Marcacion> findByFechaRange(String fechaInicio, String fechaFin, Integer page, Integer size) {
        return repository.findByFechaRange(fechaInicio, fechaFin, PageRequest.of(page, size));
    }

    public List<Marcacion> findBySucursalEntradaId(Long sucursalId) {
        return repository.findBySucursalEntradaId(sucursalId);
    }

    public List<Marcacion> findBySucursalSalidaId(Long sucursalId) {
        return repository.findBySucursalSalidaId(sucursalId);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Marcacion save(Marcacion marcacion) {
        prepararMarcacion(marcacion);

        // Guardamos el estado transiente antes de la persistencia
        Boolean esSalidaAlmuerzo = marcacion.getEsSalidaAlmuerzo();

        Marcacion marcacionGuardada = super.save(marcacion);

        // Restauramos el estado transiente y dependencias
        restaurarEstado(marcacionGuardada, marcacion, esSalidaAlmuerzo);

        procesarJornada(marcacionGuardada);

        return marcacionGuardada;
    }

    private void prepararMarcacion(Marcacion marcacion) {
        if (marcacion.getId() == null) {
            asignarNuevoId(marcacion);
            if (marcacion.getFechaEntrada() == null && marcacion.getFechaSalida() == null) {
                marcacion.setFechaEntrada(LocalDateTime.now());
            }
        }

        if (marcacion.getTipo() == null) {
            marcacion.setTipo(marcacion.getFechaSalida() != null ? TipoMarcacion.SALIDA : TipoMarcacion.ENTRADA);
        }
    }

    private void asignarNuevoId(Marcacion marcacion) {
        Long lastId = repository.findMaxId(marcacion.getSucursalId());
        long newId = (lastId == null ? 0L : lastId) + 1L;
        if (newId % 2 == 0)
            newId++;
        marcacion.setId(newId);
    }

    private void restaurarEstado(Marcacion guardada, Marcacion original, Boolean esSalidaAlmuerzo) {
        if (guardada.getUsuario() == null && original.getUsuario() != null) {
            guardada.setUsuario(original.getUsuario());
        }
        guardada.setEsSalidaAlmuerzo(esSalidaAlmuerzo);
    }

    private void procesarJornada(Marcacion marcacion) {
        try {
            if (marcacion.getUsuario() == null || marcacion.getUsuario().getId() == null) {
                log.warn("No se puede procesar jornada: Marcación sin usuario. ID: {}", marcacion.getId());
                return;
            }

            LocalDateTime fechaReferencia = obtenerFechaReferencia(marcacion);
            Horario horario = horarioResolver.resolver(marcacion, fechaReferencia);
            Jornada jornada = jornadaMarcacionResolver.resolver(marcacion, fechaReferencia.toLocalDate());

            if (horario != null && jornada.getHoraEntradaHorario() == null) {
                aplicarHorarioAJornada(jornada, horario);
            }

            almuerzoProcessor.procesar(jornada, marcacion);
            tardanzaCalculator.calcular(jornada);
            horasTrabajadasCalculator.calcular(jornada);

            jornadaService.save(jornada);

        } catch (Exception e) {
            log.error("Error crítico procesando jornada para marcación ID: {} en sucursal: {}",
                    marcacion.getId(), marcacion.getSucursalId(), e);
        }
    }

    private LocalDateTime obtenerFechaReferencia(Marcacion marcacion) {
        if (marcacion.getFechaEntrada() != null)
            return marcacion.getFechaEntrada();
        if (marcacion.getFechaSalida() != null)
            return marcacion.getFechaSalida();
        return LocalDateTime.now();
    }

    private void aplicarHorarioAJornada(Jornada jornada, Horario horario) {
        jornada.setTurno(horario.getTurno());
        jornada.setHoraEntradaHorario(horario.getHoraEntrada());
        jornada.setHoraSalidaHorario(horario.getHoraSalida());
        jornada.setInicioDescansoHorario(horario.getInicioDescanso());
        jornada.setFinDescansoHorario(horario.getFinDescanso());
        jornada.setToleranciaMinutosHorario(horario.getToleranciaMinutos());
    }
}