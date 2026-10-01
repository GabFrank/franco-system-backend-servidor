package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.LiquidacionItemProgramado;
import com.franco.dev.domain.rrhh.enums.LiquidacionItemProgramadoEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemProgramadoRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.franco.dev.domain.rrhh.LiquidacionItemProgramado.REFERENCIA_TIPO;

/**
 * Aplicacion de los items programados ({@link LiquidacionItemProgramado#REFERENCIA_TIPO}) desde una
 * liquidacion mensual o un finiquito: cuales entran en un borrador, validacion antes de mover plata y
 * aplicar/revertir al pagar/anular. Espejo de {@link ValeCuotaDescuentoService}; depende solo de
 * repositorios para no cerrar un ciclo de beans con los servicios de liquidacion.
 */
@Service
@AllArgsConstructor
public class ItemProgramadoAplicacionService {

    static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final LiquidacionItemProgramadoRepository repository;
    private final LiquidacionItemRepository liquidacionItemRepository;
    private final LiquidacionFinalItemRepository liquidacionFinalItemRepository;

    // ─────────────────────────── cuales entran ───────────────────────────

    /** Los PENDIENTE del funcionario para ese periodo, sin los que ya estan en otro documento vivo. */
    @Transactional(readOnly = true)
    public List<LiquidacionItemProgramado> paraLiquidacion(Long funcionarioId, String periodo, Long liquidacionId) {
        List<LiquidacionItemProgramado> pendientes = repository.findByFuncionarioIdAndPeriodoAndEstadoOrderByIdAsc(
                funcionarioId, periodo, LiquidacionItemProgramadoEstado.PENDIENTE);
        return sinLosDeOtrosDocumentos(pendientes, liquidacionId, null);
    }

    /**
     * Todos los PENDIENTE del funcionario, sin importar el periodo (se va y no hay liquidaciones futuras),
     * salvo los que ya estan en una liquidacion mensual o en otro finiquito vivo.
     */
    @Transactional(readOnly = true)
    public List<LiquidacionItemProgramado> paraFiniquito(Long funcionarioId, Long liquidacionFinalId) {
        List<LiquidacionItemProgramado> pendientes = repository.findByFuncionarioIdAndEstadoOrderByPeriodoAscIdAsc(
                funcionarioId, LiquidacionItemProgramadoEstado.PENDIENTE);
        return sinLosDeOtrosDocumentos(pendientes, null, liquidacionFinalId);
    }

    // ─────────────────────────── validar antes de mover plata ───────────────────────────

    @Transactional
    public void validarLiquidacion(Long liquidacionId) {
        Map<Long, BigDecimal> porProgramado = new TreeMap<>();
        for (LiquidacionItem it : liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId)) {
            acumular(porProgramado, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porProgramado, "la liquidacion #" + liquidacionId, liquidacionId, null);
    }

    @Transactional
    public void validarFiniquito(Long liquidacionFinalId) {
        Map<Long, BigDecimal> porProgramado = new TreeMap<>();
        for (LiquidacionFinalItem it : liquidacionFinalItemRepository.findByLiquidacionFinalIdOrderByIdAsc(liquidacionFinalId)) {
            acumular(porProgramado, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porProgramado, "el finiquito #" + liquidacionFinalId, null, liquidacionFinalId);
    }

    // ─────────────────────────── aplicar / revertir ───────────────────────────

    @Transactional
    public void aplicarLiquidacion(Long programadoId, BigDecimal monto, Long liquidacionId) {
        aplicar(programadoId, monto, liquidacionId, null);
    }

    @Transactional
    public void aplicarFiniquito(Long programadoId, BigDecimal monto, Long liquidacionFinalId) {
        aplicar(programadoId, monto, null, liquidacionFinalId);
    }

    @Transactional
    public void revertirLiquidacion(Long programadoId, Long liquidacionId) {
        revertir(programadoId, liquidacionId, null);
    }

    @Transactional
    public void revertirFiniquito(Long programadoId, Long liquidacionFinalId) {
        revertir(programadoId, null, liquidacionFinalId);
    }

    /**
     * Marca el programado APLICADO por este documento. Ya aplicado por el mismo documento → no-op (el hook
     * de tesoreria puede reentrar); por otro, inexistente, ANULADO o con otro monto → lanza y el pago entero
     * se deshace.
     */
    private void aplicar(Long programadoId, BigDecimal monto, Long liquidacionId, Long liquidacionFinalId) {
        LiquidacionItemProgramado p = repository.lockById(programadoId).orElse(null);
        if (p != null && p.getEstado() == LiquidacionItemProgramadoEstado.APLICADO
                && mismoDocumento(p, liquidacionId, liquidacionFinalId)) {
            return;
        }
        if (p == null || p.getEstado() != LiquidacionItemProgramadoEstado.PENDIENTE
                || nz(p.getMonto()).subtract(nz(monto)).abs().compareTo(TOLERANCIA) > 0) {
            // Sin montos ni ids: esta guarda tambien la alcanza un pago desde el hub de tesoreria.
            throw new GraphQLException("Un item programado de este documento cambio desde que se genero."
                    + " Vuelva a borrador y regenere.");
        }
        p.setEstado(LiquidacionItemProgramadoEstado.APLICADO);
        p.setLiquidacionId(liquidacionId);
        p.setLiquidacionFinalId(liquidacionFinalId);
        repository.save(p);
    }

    private void revertir(Long programadoId, Long liquidacionId, Long liquidacionFinalId) {
        LiquidacionItemProgramado p = repository.lockById(programadoId).orElse(null);
        if (p == null || p.getEstado() != LiquidacionItemProgramadoEstado.APLICADO
                || !mismoDocumento(p, liquidacionId, liquidacionFinalId)) {
            return;
        }
        p.setEstado(LiquidacionItemProgramadoEstado.PENDIENTE);
        p.setLiquidacionId(null);
        p.setLiquidacionFinalId(null);
        repository.save(p);
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private List<LiquidacionItemProgramado> sinLosDeOtrosDocumentos(List<LiquidacionItemProgramado> programados,
                                                                   Long liquidacionId, Long liquidacionFinalId) {
        if (programados.isEmpty()) return programados;
        List<Long> ids = programados.stream().map(LiquidacionItemProgramado::getId).collect(Collectors.toList());
        Set<Long> ocupados = new HashSet<>(liquidacionItemRepository.findReferenciasEnOtrasLiquidaciones(
                REFERENCIA_TIPO, ids, liquidacionId));
        ocupados.addAll(liquidacionFinalItemRepository.findReferenciasEnOtrosFiniquitos(
                REFERENCIA_TIPO, ids, liquidacionFinalId));
        return programados.stream().filter(p -> !ocupados.contains(p.getId())).collect(Collectors.toList());
    }

    private static boolean mismoDocumento(LiquidacionItemProgramado p, Long liquidacionId, Long liquidacionFinalId) {
        return liquidacionId != null ? liquidacionId.equals(p.getLiquidacionId())
                : liquidacionFinalId != null && liquidacionFinalId.equals(p.getLiquidacionFinalId());
    }

    private static void acumular(Map<Long, BigDecimal> porProgramado, String tipo, Long refId, BigDecimal monto) {
        if (!REFERENCIA_TIPO.equals(tipo) || refId == null) return;
        porProgramado.merge(refId, nz(monto), BigDecimal::add);
    }

    /** Recorre en orden de id (TreeMap): orden canonico de locks. */
    private void validar(Map<Long, BigDecimal> porProgramado, String documento, Long liquidacionId, Long liquidacionFinalId) {
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : porProgramado.entrySet()) {
            LiquidacionItemProgramado p = repository.lockById(e.getKey()).orElse(null);
            if (p == null) {
                problemas.add("el item programado #" + e.getKey() + " ya no existe");
                continue;
            }
            if (p.getEstado() == LiquidacionItemProgramadoEstado.APLICADO && mismoDocumento(p, liquidacionId, liquidacionFinalId)) {
                continue;
            }
            String etiqueta = "item programado #" + p.getId() + " (" + p.getDescripcion() + ")";
            if (p.getEstado() != LiquidacionItemProgramadoEstado.PENDIENTE) {
                problemas.add(etiqueta + " ya esta " + p.getEstado());
            } else if (nz(p.getMonto()).subtract(e.getValue()).abs().compareTo(TOLERANCIA) > 0) {
                problemas.add(etiqueta + ": programado " + nz(p.getMonto()).toPlainString()
                        + ", en el documento " + e.getValue().toPlainString());
            }
        }
        if (!problemas.isEmpty()) {
            throw new GraphQLException("No se puede pagar " + documento
                    + ": cambiaron items programados desde que se genero (" + String.join("; ", problemas)
                    + "). Vuelva a borrador y regenere.");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
