package com.franco.dev.service.rrhh;

import com.franco.dev.service.rrhh.builder.PenalizacionCalculator;

import com.franco.dev.domain.administrativo.Jornada;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.rrhh.Justificativo;
import com.franco.dev.domain.rrhh.Penalizacion;
import com.franco.dev.domain.rrhh.enums.PenalizacionTipo;
import com.franco.dev.repository.rrhh.PenalizacionRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.administrativo.JornadaService;
import com.franco.dev.service.personas.FuncionarioService;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final JustificativoService justificativoService;
    private final ConfiguracionRrhhService configuracionRrhhService;
    private final PlatformTransactionManager transactionManager;

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

    public Page<Penalizacion> findPage(Long funcionarioId, LocalDate desde, LocalDate hasta, PenalizacionTipo tipo,
                                       Pageable pageable) {
        return repository.findPage(funcionarioId, desde, hasta, tipo, pageable);
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
        long toleranciaMin = configuracionRrhhService.getNumber("TOLERANCIA_TARDANZA_MIN", BigDecimal.ZERO).longValue();

        String f = fecha.toString();
        List<Jornada> jornadas = jornadaService.findByFechaRange(f, f);
        int generadas = 0;

        for (Jornada j : jornadas) {
            Long tardia = j.getMinutosLlegadaTardia();
            // dentro de la tolerancia configurada no se penaliza (grace period)
            if (tardia == null || tardia <= toleranciaMin) continue;
            if (j.getUsuario() == null) continue;

            // j.getUsuario() es la cuenta que marco la jornada (el empleado). El
            // funcionario se resuelve por la persona compartida, NO por
            // funcionario.usuario_id, que es "creado_por" (auditoria): esa columna
            // reventaba el job con NonUniqueResultException cuando un usuario habia
            // creado mas de un funcionario.
            Funcionario func = j.getUsuario().getPersona() != null
                    ? funcionarioService.findByPersonaId(j.getUsuario().getPersona().getId())
                    : null;
            if (func == null) continue;

            // saltear si la jornada tiene un justificativo cuyo tipo evita la penalizacion.
            // El comportamiento lo define el catalogo (TipoJustificativo.evitaPenalizacion),
            // no un enum fijo: asi se pueden agregar tipos sin desplegar codigo.
            List<Justificativo> justificativos = justificativoService.findByJornada(j.getId(), j.getSucursalId());
            boolean justificada = justificativos.stream()
                    .anyMatch(x -> x.getTipo() != null && Boolean.TRUE.equals(x.getTipo().getEvitaPenalizacion()));
            if (justificada) continue;

            // idempotencia: saltear si ya hay penalizacion auto no anulada para esta jornada
            List<Penalizacion> existentes = repository
                    .findByJornadaIdAndSucursalIdAndAutoGeneradaTrueAndAnuladaFalse(j.getId(), j.getSucursalId());
            if (!existentes.isEmpty()) continue;

            BigDecimal monto = PenalizacionCalculator.calcularMontoTardanza(montoFijo, montoPorMin, tardia);

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

    /** Tope de dias por corrida. Evita recorrer meses de jornadas por un error de tipeo. */
    public static final int RANGO_MAX_DIAS = 62;

    /**
     * Genera las penalizaciones automaticas de un rango de fechas, un dia por vez.
     *
     * <p>Reusa el metodo de un dia en vez de duplicar la logica, asi la tolerancia, los
     * justificativos que evitan penalizacion y la idempotencia por jornada siguen siendo
     * las mismas. La guarda de idempotencia es por jornada, no por fecha, asi que correr
     * 1-5 y despues 3-7 no duplica los dias que se solapan.</p>
     *
     * <p>Cada dia va en su propia transaccion con TransactionTemplate y no anotando
     * REQUIRES_NEW: la llamada de abajo es self-invocation (misma clase), no pasa por el
     * proxy de Spring y la anotacion se ignoraria en silencio. Ademas, un dia que falle no
     * se lleva puestos a los anteriores, que ya commitearon.</p>
     */
    public int generarPenalizacionesAutoRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new GraphQLException("Indica el rango de fechas");
        if (hasta.isBefore(desde)) throw new GraphQLException("La fecha final no puede ser anterior a la inicial");
        long dias = java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > RANGO_MAX_DIAS) {
            throw new GraphQLException("El rango no puede superar " + RANGO_MAX_DIAS + " dias (pediste " + dias + ")");
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int total = 0;
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            final LocalDate dia = d;
            try {
                Integer n = tx.execute(status -> generarPenalizacionesAuto(dia));
                if (n != null) total += n;
            } catch (Exception e) {
                LOGGER.warn("Fallo la generacion automatica del {}: {}", dia, e.getMessage());
            }
        }
        LOGGER.info("Penalizaciones automaticas generadas entre {} y {}: {}", desde, hasta, total);
        return total;
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
