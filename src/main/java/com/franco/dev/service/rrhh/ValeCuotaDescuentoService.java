package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.Vale;
import com.franco.dev.domain.rrhh.ValeCuota;
import com.franco.dev.domain.rrhh.enums.ValeCuotaEstado;
import com.franco.dev.domain.rrhh.enums.ValeEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.ValeCuotaRepository;
import com.franco.dev.repository.rrhh.ValeRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Descuento de cuotas de vale ({@link ValeService#REFERENCIA_CUOTA}) desde una liquidacion mensual o un
 * finiquito. Espejo de {@link PrestamoCuotaDescuentoService}: elige que cuotas entran en un borrador,
 * valida antes de mover plata y aplica/revierte al pagar/anular.
 *
 * <p>Una cuota se descuenta una sola vez: no entra en un borrador si ya esta en otro documento vivo, y al
 * pagar se rechaza si otro documento ya la desconto. Depende solo de repositorios: lo usan los dos
 * servicios de liquidacion y el hub de tesoreria.</p>
 */
@Service
@AllArgsConstructor
public class ValeCuotaDescuentoService {

    static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final ValeCuotaRepository cuotaRepository;
    private final ValeRepository valeRepository;
    private final LiquidacionItemRepository liquidacionItemRepository;
    private final LiquidacionFinalItemRepository liquidacionFinalItemRepository;

    // ─────────────────────────── que cuotas entran ───────────────────────────

    /**
     * Cuotas del vale a descontar en la liquidacion mensual: PENDIENTE con fecha de descuento hasta el fin
     * del periodo (una atrasada entra igual), sin las que ya estan en otra liquidacion o en un finiquito vivo.
     */
    @Transactional(readOnly = true)
    public List<ValeCuota> cuotasParaLiquidacion(Vale vale, LocalDate fin, Long liquidacionId) {
        List<ValeCuota> pendientes = pendientes(vale).stream()
                .filter(c -> c.getFechaDescuento() != null && !c.getFechaDescuento().isAfter(fin))
                .collect(Collectors.toList());
        return sinLasDeOtrosDocumentos(pendientes, liquidacionId, null);
    }

    /** Cuotas del vale a descontar en el finiquito: todas las PENDIENTE (el funcionario se va y salda todo). */
    @Transactional(readOnly = true)
    public List<ValeCuota> cuotasParaFiniquito(Vale vale, Long liquidacionFinalId) {
        return sinLasDeOtrosDocumentos(pendientes(vale), null, liquidacionFinalId);
    }

    /** "VALE UNIFORME 1/2", "ADELANTO DE SUELDO 2/3". */
    public static String descripcion(Vale vale, ValeCuota cuota) {
        String base = Boolean.TRUE.equals(vale.getEsAdelanto()) ? "ADELANTO DE SUELDO"
                : vale.getMotivo() != null && vale.getMotivo().getNombre() != null
                ? "VALE " + vale.getMotivo().getNombre().toUpperCase().trim() : "VALE";
        return base + " " + cuota.getNumero() + "/" + vale.getCantidadCuotas();
    }

    // ─────────────────────────── validar antes de mover plata ───────────────────────────

    /** Rechaza el pago de la liquidacion mensual si alguna de sus cuotas de vale cambio desde que se genero. */
    @Transactional
    public void validarLiquidacion(Long liquidacionId) {
        Map<Long, BigDecimal> porCuota = new TreeMap<>();
        for (LiquidacionItem it : liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId)) {
            acumular(porCuota, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porCuota, "la liquidacion #" + liquidacionId, liquidacionId, null);
    }

    /** Rechaza el pago del finiquito si alguna de sus cuotas de vale cambio desde que se genero. */
    @Transactional
    public void validarFiniquito(Long liquidacionFinalId) {
        Map<Long, BigDecimal> porCuota = new TreeMap<>();
        for (LiquidacionFinalItem it : liquidacionFinalItemRepository.findByLiquidacionFinalIdOrderByIdAsc(liquidacionFinalId)) {
            acumular(porCuota, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porCuota, "el finiquito #" + liquidacionFinalId, null, liquidacionFinalId);
    }

    // ─────────────────────────── aplicar / revertir ───────────────────────────

    @Transactional
    public void aplicarLiquidacion(Long cuotaId, BigDecimal monto, Long liquidacionId) {
        aplicar(cuotaId, monto, liquidacionId, null);
    }

    @Transactional
    public void aplicarFiniquito(Long cuotaId, BigDecimal monto, Long liquidacionFinalId) {
        aplicar(cuotaId, monto, null, liquidacionFinalId);
    }

    @Transactional
    public void revertirLiquidacion(Long cuotaId, Long liquidacionId) {
        revertir(cuotaId, liquidacionId, null);
    }

    @Transactional
    public void revertirFiniquito(Long cuotaId, Long liquidacionFinalId) {
        revertir(cuotaId, null, liquidacionFinalId);
    }

    /**
     * Marca la cuota DESCONTADA por este documento. La misma cuota ya descontada por el mismo documento es un
     * no-op (el hook de tesoreria puede reentrar); por otro documento, o inexistente/ANULADA, lanza y el pago
     * entero se deshace. Si era la ultima pendiente, el vale queda DESCONTADO.
     */
    private void aplicar(Long cuotaId, BigDecimal monto, Long liquidacionId, Long liquidacionFinalId) {
        ValeCuota c = cuotaRepository.lockById(cuotaId).orElse(null);
        if (c != null && c.getEstado() == ValeCuotaEstado.DESCONTADA
                && mismoDocumento(c, liquidacionId, liquidacionFinalId)) {
            return;
        }
        if (c == null || c.getEstado() != ValeCuotaEstado.PENDIENTE
                || nz(c.getMonto()).subtract(nz(monto)).abs().compareTo(TOLERANCIA) > 0) {
            // Sin montos ni ids: esta guarda tambien la alcanza un pago desde el hub generico de tesoreria.
            throw new GraphQLException("Una cuota de vale descontada en este documento cambio desde que se"
                    + " genero. Vuelva a borrador y regenere.");
        }
        c.setEstado(ValeCuotaEstado.DESCONTADA);
        c.setLiquidacionId(liquidacionId);
        c.setLiquidacionFinalId(liquidacionFinalId);
        cuotaRepository.save(c);

        Vale v = c.getVale();
        boolean todas = cuotaRepository.findByValeIdOrderByNumeroAsc(v.getId()).stream()
                .allMatch(x -> x.getEstado() == ValeCuotaEstado.DESCONTADA);
        if (todas) {
            v.setEstado(ValeEstado.DESCONTADO);
            if (liquidacionId != null) v.setLiquidacionId(liquidacionId);
            valeRepository.save(v);
        }
    }

    /** Devuelve a PENDIENTE la cuota que desconto este documento, y el vale a CONFIRMADO si estaba DESCONTADO. */
    private void revertir(Long cuotaId, Long liquidacionId, Long liquidacionFinalId) {
        ValeCuota c = cuotaRepository.lockById(cuotaId).orElse(null);
        if (c == null || c.getEstado() != ValeCuotaEstado.DESCONTADA
                || !mismoDocumento(c, liquidacionId, liquidacionFinalId)) {
            return;
        }
        c.setEstado(ValeCuotaEstado.PENDIENTE);
        c.setLiquidacionId(null);
        c.setLiquidacionFinalId(null);
        cuotaRepository.save(c);

        Vale v = c.getVale();
        if (v.getEstado() == ValeEstado.DESCONTADO) {
            v.setEstado(ValeEstado.CONFIRMADO);
            v.setLiquidacionId(null);
            valeRepository.save(v);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private List<ValeCuota> pendientes(Vale vale) {
        return cuotaRepository.findByValeIdOrderByNumeroAsc(vale.getId()).stream()
                .filter(c -> c.getEstado() == ValeCuotaEstado.PENDIENTE)
                .collect(Collectors.toList());
    }

    private List<ValeCuota> sinLasDeOtrosDocumentos(List<ValeCuota> cuotas, Long liquidacionId,
                                                    Long liquidacionFinalId) {
        if (cuotas.isEmpty()) return cuotas;
        List<Long> ids = cuotas.stream().map(ValeCuota::getId).collect(Collectors.toList());
        Set<Long> ocupadas = new HashSet<>(liquidacionItemRepository.findCuotasDeValeEnOtrasLiquidaciones(ids, liquidacionId));
        ocupadas.addAll(liquidacionFinalItemRepository.findCuotasDeValeEnOtrosFiniquitos(ids, liquidacionFinalId));
        return cuotas.stream().filter(c -> !ocupadas.contains(c.getId())).collect(Collectors.toList());
    }

    private static boolean mismoDocumento(ValeCuota c, Long liquidacionId, Long liquidacionFinalId) {
        return liquidacionId != null ? liquidacionId.equals(c.getLiquidacionId())
                : liquidacionFinalId != null && liquidacionFinalId.equals(c.getLiquidacionFinalId());
    }

    private static void acumular(Map<Long, BigDecimal> porCuota, String tipo, Long refId, BigDecimal monto) {
        if (!ValeService.REFERENCIA_CUOTA.equals(tipo) || refId == null) return;
        porCuota.merge(refId, nz(monto), BigDecimal::add);
    }

    /** Recorre las cuotas en orden de id (TreeMap): orden canonico de locks. */
    private void validar(Map<Long, BigDecimal> porCuota, String documento, Long liquidacionId, Long liquidacionFinalId) {
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : porCuota.entrySet()) {
            ValeCuota c = cuotaRepository.lockById(e.getKey()).orElse(null);
            if (c == null) {
                problemas.add("la cuota de vale #" + e.getKey() + " ya no existe");
                continue;
            }
            String etiqueta = "cuota " + c.getNumero() + " del vale #" + c.getVale().getId();
            if (c.getEstado() == ValeCuotaEstado.DESCONTADA && mismoDocumento(c, liquidacionId, liquidacionFinalId)) {
                continue;
            }
            if (c.getEstado() != ValeCuotaEstado.PENDIENTE) {
                problemas.add(etiqueta + " ya esta " + c.getEstado());
            } else if (c.getVale().getEstado() != ValeEstado.CONFIRMADO) {
                problemas.add(etiqueta + ": el vale esta " + c.getVale().getEstado());
            } else if (nz(c.getMonto()).subtract(e.getValue()).abs().compareTo(TOLERANCIA) > 0) {
                problemas.add(etiqueta + ": cuota " + nz(c.getMonto()).toPlainString()
                        + ", descontado " + e.getValue().toPlainString());
            }
        }
        if (!problemas.isEmpty()) {
            throw new GraphQLException("No se puede pagar " + documento
                    + ": cambiaron cuotas de vale desde que se genero (" + String.join("; ", problemas)
                    + "). Vuelva a borrador y regenere.");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
