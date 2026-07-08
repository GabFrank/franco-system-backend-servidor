package com.franco.dev.service.rrhh;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.JornadaNovedad;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.enums.JornadaNovedadTipo;
import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import com.franco.dev.repository.rrhh.PenalizacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.personas.FuncionarioService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class PenalizacionService extends CrudService<Penalizacion, PenalizacionRepository, Long> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PenalizacionService.class);

    private final PenalizacionRepository repository;
    private final JornadaService jornadaService;
    private final FuncionarioService funcionarioService;
    private final JornadaNovedadService jornadaNovedadService;
    private final ConfiguracionRrhhService configuracionRrhhService;

    @Override
    public PenalizacionRepository getRepository() {
        return repository;
    }

    public List<Penalizacion> findByFuncionarioId(Long funcionarioId) {
        return repository.findByFuncionarioIdOrderByFechaDesc(funcionarioId);
    }

    public List<Penalizacion> findByFuncionarioIdAndFechaBetween(Long funcionarioId, LocalDate desde, LocalDate hasta) {
        return repository.findByFuncionarioIdAndFechaBetweenAndAnuladaFalse(funcionarioId, desde, hasta);
    }

    public List<Penalizacion> findByJornada(Long jornadaId, Long sucursalId) {
        return repository.findByJornadaIdAndSucursalId(jornadaId, sucursalId);
    }

    @Transactional
    public Penalizacion anular(Long id) {
        Optional<Penalizacion> opt = repository.findById(id);
        if (opt.isEmpty()) return null;
        Penalizacion e = opt.get();
        e.setAnulada(true);
        return repository.save(e);
    }

    /**
     * Anula las penalizaciones auto-generadas no anuladas de una jornada.
     * La usa el flujo de justificacion de jornada.
     */
    @Transactional
    public int anularAutoDeJornada(Long jornadaId, Long sucursalId) {
        List<Penalizacion> autos = repository
                .findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse(jornadaId, sucursalId);
        for (Penalizacion p : autos) {
            p.setAnulada(true);
            repository.save(p);
        }
        return autos.size();
    }

    /**
     * Recorre las jornadas de la fecha dada con llegada tardia y genera
     * penalizaciones automaticas segun la configuracion RRHH. Idempotente:
     * saltea jornadas ya justificadas o ya penalizadas. Espejo de la formula
     * de FRC Gourmet: monto = fijo + porMinuto x minutos.
     */
    @Transactional
    public int generarPenalizacionesAuto(LocalDate fecha) {
        if (fecha == null) return 0;
        if (!configuracionRrhhService.getBoolean("PENALIZACION_AUTO_TARDANZA", true)) return 0;

        BigDecimal montoFijo = configuracionRrhhService.getNumber("PENALIZACION_MONTO_TARDANZA", BigDecimal.ZERO);
        BigDecimal montoPorMin = configuracionRrhhService.getNumber("PENALIZACION_MONTO_POR_MINUTO_TARDANZA", BigDecimal.ZERO);

        String f = fecha.toString();
        List<Jornada> jornadas = jornadaService.findByFechaRange(f, f);
        int generadas = 0;

        for (Jornada j : jornadas) {
            Long tardia = j.getMinutosLlegadaTardia();
            if (tardia == null || tardia <= 0) continue;
            if (j.getUsuario() == null) continue;

            Funcionario func = funcionarioService.findByUsuarioId(j.getUsuario().getId());
            if (func == null) continue;

            // saltear si la jornada esta justificada (novedad JUSTIFICADO)
            List<JornadaNovedad> novedades = jornadaNovedadService.findByJornada(j.getId(), j.getSucursalId());
            boolean justificada = novedades.stream().anyMatch(n -> n.getTipo() == JornadaNovedadTipo.JUSTIFICADO);
            if (justificada) continue;

            // idempotencia: saltear si ya hay penalizacion auto no anulada para esta jornada
            List<Penalizacion> existentes = repository
                    .findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse(j.getId(), j.getSucursalId());
            if (!existentes.isEmpty()) continue;

            BigDecimal monto = montoFijo.add(montoPorMin.multiply(new BigDecimal(tardia)));

            Penalizacion p = new Penalizacion();
            p.setFuncionario(func);
            p.setJornadaId(j.getId());
            p.setSucursalId(j.getSucursalId());
            p.setTipo(PenalizacionTipo.TARDANZA);
            p.setDescripcion("PENALIZACION AUTOMATICA POR TARDANZA (" + tardia + " MIN)");
            p.setMonto(monto);
            p.setFecha(j.getFecha());
            p.setAutoGenerada(true);
            p.setAnulada(false);
            p.setCreadoEn(LocalDateTime.now());
            repository.save(p);
            generadas++;
        }

        if (generadas > 0) {
            LOGGER.info("Penalizaciones automaticas generadas para {}: {}", fecha, generadas);
        }
        return generadas;
    }

    @Override
    public Penalizacion save(Penalizacion entity) {
        if (entity.getId() == null && entity.getCreadoEn() == null)
            entity.setCreadoEn(LocalDateTime.now());
        if (entity.getAnulada() == null) entity.setAnulada(false);
        if (entity.getAutoGenerada() == null) entity.setAutoGenerada(false);
        if (entity.getMonto() == null) entity.setMonto(BigDecimal.ZERO);
        if (entity.getDescripcion() != null) entity.setDescripcion(entity.getDescripcion().toUpperCase());
        return super.save(entity);
    }
}
