package com.franco.dev.service.rrhh;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.LiquidacionSueldo;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemTipo;
import com.franco.dev.domain.rrhh.enums.LiquidacionSueldoEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemProgramadoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionSueldoRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Ítems de liquidación programados para otro periodo: se cargan desde una liquidación y se aplican en la
 * de un periodo posterior, que todavía no existe (o ya está en borrador). La liquidación del periodo los
 * toma como ítems automáticos {@link LiquidacionItemProgramado#REFERENCIA_TIPO}.
 */
@Service
@AllArgsConstructor
public class LiquidacionItemProgramadoService {

    /** Hasta cuántos meses después de la liquidación de origen se puede programar. */
    public static final int MAX_MESES = 12;

    private final LiquidacionItemProgramadoRepository repository;
    private final LiquidacionSueldoRepository liquidacionRepository;
    private final LiquidacionItemRepository liquidacionItemRepository;
    private final LiquidacionFinalItemRepository liquidacionFinalItemRepository;
    private final LiquidacionSueldoService liquidacionSueldoService;

    @Transactional(readOnly = true)
    public List<LiquidacionItemProgramado> findPorFuncionario(Long funcionarioId, LiquidacionItemProgramadoEstado estado) {
        return estado != null
                ? repository.findByFuncionarioIdAndEstadoOrderByPeriodoAscIdAsc(funcionarioId, estado)
                : repository.findByFuncionarioIdOrderByPeriodoDescIdDesc(funcionarioId);
    }

    /** PENDIENTE con un periodo que ya pasó: nadie liquidó ese mes (o el funcionario ya no está). */
    public static boolean vencido(LiquidacionItemProgramado p, YearMonth hoy) {
        return p.getEstado() == LiquidacionItemProgramadoEstado.PENDIENTE && p.getPeriodo() != null
                && YearMonth.parse(p.getPeriodo()).isBefore(hoy);
    }

    /**
     * Programa un ítem para {@code periodo}, posterior al de la liquidación de origen y a lo sumo
     * {@link #MAX_MESES} meses después. Mismas reglas que el ítem manual: el signo sale del catálogo.
     * Si la liquidación del periodo ya existe en BORRADOR, entra ahí en el momento; APROBADA o PAGADA,
     * se rechaza; ANULADA cuenta como inexistente (al regenerarla vuelve a borrador y lo toma).
     */
    @Transactional
    public LiquidacionItemProgramado programar(Long liquidacionOrigenId, String periodo, String descripcion,
                                               BigDecimal monto, LiquidacionItemTipo tipo,
                                               Long liquidacionConceptoId, Usuario usuario) {
        LiquidacionSueldo origen = liquidacionRepository.findById(liquidacionOrigenId)
                .orElseThrow(() -> new GraphQLException("Liquidacion no encontrada"));
        YearMonth destinoYm = parsear(periodo);
        YearMonth origenYm = parsear(origen.getPeriodo());
        if (!destinoYm.isAfter(origenYm)) {
            throw new GraphQLException("El periodo tiene que ser posterior a " + origen.getPeriodo()
                    + ": para ese mismo periodo agregá el ítem en la liquidación");
        }
        if (destinoYm.isAfter(origenYm.plusMonths(MAX_MESES))) {
            throw new GraphQLException("Se puede programar hasta " + MAX_MESES + " meses después ("
                    + origenYm.plusMonths(MAX_MESES) + ")");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new GraphQLException("El monto del ítem debe ser positivo");
        }
        Long funcionarioId = origen.getFuncionario().getId();
        LiquidacionSueldo destino = liquidacionRepository.findByFuncionarioIdAndPeriodo(funcionarioId, periodo).orElse(null);
        if (destino != null && (destino.getEstado() == LiquidacionSueldoEstado.APROBADA
                || destino.getEstado() == LiquidacionSueldoEstado.PAGADA)) {
            throw new GraphQLException("La liquidacion de " + periodo + " ya esta " + destino.getEstado()
                    + ": no se le pueden agregar ítems");
        }

        LiquidacionSueldoService.ItemManualResuelto r =
                liquidacionSueldoService.resolverItemManual(descripcion, tipo, liquidacionConceptoId);
        LiquidacionItemProgramado p = new LiquidacionItemProgramado();
        p.setFuncionario(origen.getFuncionario());
        p.setPeriodo(periodo);
        p.setLiquidacionConceptoId(liquidacionConceptoId);
        p.setCodigo(r.codigo);
        p.setTipo(r.tipo);
        p.setDescripcion(r.descripcion);
        p.setMonto(monto);
        p.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        p.setOrigenLiquidacionId(origen.getId());
        p.setUsuario(usuario);
        p = repository.save(p);

        if (destino != null && destino.getEstado() == LiquidacionSueldoEstado.BORRADOR) {
            liquidacionSueldoService.agregarItemProgramado(destino, p);
        }
        return p;
    }

    /**
     * Anula un programado que todavía no se aplicó. Si está en un BORRADOR, se saca ese ítem y se
     * recalculan los totales; en una liquidación APROBADA/PAGADA o en un finiquito vivo, se rechaza.
     */
    @Transactional
    public LiquidacionItemProgramado anular(Long id) {
        LiquidacionItemProgramado p = repository.lockById(id)
                .orElseThrow(() -> new GraphQLException("Item programado no encontrado"));
        if (p.getEstado() == LiquidacionItemProgramadoEstado.ANULADO) return p;
        if (p.getEstado() == LiquidacionItemProgramadoEstado.APLICADO) {
            throw new GraphQLException("El item programado ya se aplico en la liquidacion #" + p.getLiquidacionId()
                    + (p.getLiquidacionFinalId() != null ? " / finiquito #" + p.getLiquidacionFinalId() : ""));
        }
        List<Long> finiquitos = liquidacionFinalItemRepository.findFiniquitosVivosConProgramado(id);
        if (!finiquitos.isEmpty()) {
            throw new GraphQLException("El item programado esta en el finiquito #" + finiquitos.get(0)
                    + ": anulalo o volvé a generarlo antes");
        }
        List<LiquidacionSueldo> liquidaciones = liquidacionItemRepository.findLiquidacionesVivasConProgramado(id);
        for (LiquidacionSueldo l : liquidaciones) {
            if (l.getEstado() != LiquidacionSueldoEstado.BORRADOR) {
                throw new GraphQLException("El item programado esta en la liquidacion #" + l.getId()
                        + " (" + l.getEstado() + "): volvela a borrador antes de anularlo");
            }
        }
        for (LiquidacionSueldo l : liquidaciones) {
            liquidacionSueldoService.quitarItemProgramado(l.getId(), id);
        }
        p.setEstado(LiquidacionItemProgramadoEstado.ANULADO);
        return repository.save(p);
    }

    private static YearMonth parsear(String periodo) {
        try {
            return YearMonth.parse(periodo);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new GraphQLException("Periodo invalido, se espera 'YYYY-MM'");
        }
    }
}
