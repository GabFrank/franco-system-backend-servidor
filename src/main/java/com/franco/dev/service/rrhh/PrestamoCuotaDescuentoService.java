package com.franco.dev.service.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionFinalItem;
import com.franco.dev.domain.rrhh.LiquidacionItem;
import com.franco.dev.domain.rrhh.Prestamo;
import com.franco.dev.domain.rrhh.PrestamoCuota;
import com.franco.dev.domain.rrhh.enums.PrestamoCuotaEstado;
import com.franco.dev.domain.rrhh.enums.PrestamoEstado;
import com.franco.dev.repository.rrhh.LiquidacionFinalItemRepository;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.repository.rrhh.PrestamoCuotaRepository;
import com.franco.dev.repository.rrhh.PrestamoRepository;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Descuento de cuotas de prestamo desde una liquidacion mensual o un finiquito (issue #300).
 *
 * <p>El borrador congela el descuento de cada cuota por lo pendiente al generarlo, y ese monto ya esta
 * dentro del neto a pagar. Si la cuota se cobro por caja despues, pagar la liquidacion la cobraba dos
 * veces: en efectivo y descontada del sueldo. Por eso, antes de mover plata, se toma cada cuota con lock
 * y se compara con lo pendiente real ({@link #validarLiquidacion} / {@link #validarFiniquito}); si
 * cambio, el pago se rechaza y hay que regenerar el borrador.</p>
 *
 * <p>Es el unico que escribe cuota y prestamo desde una liquidacion. Depende solo de repositorios a
 * proposito: lo usan los dos servicios de liquidacion y el hub de tesoreria, y no puede cerrar un ciclo
 * de beans con ninguno. Orden de locks: cuotas (id ascendente) → prestamo, igual que
 * {@code PrestamoService.cobrarCuota}, y siempre antes que el saldo de caja.</p>
 */
@Service
@AllArgsConstructor
public class PrestamoCuotaDescuentoService {

    /** El referenciaTipo de los items que descuentan una cuota de prestamo. */
    public static final String REFERENCIA_CUOTA = "CPP_CUOTA";

    /** Misma tolerancia que tesoreria. */
    static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private final PrestamoCuotaRepository cuotaRepository;
    private final PrestamoRepository prestamoRepository;
    private final LiquidacionItemRepository liquidacionItemRepository;
    private final LiquidacionFinalItemRepository liquidacionFinalItemRepository;

    /** Rechaza el pago de la liquidacion mensual si alguna de sus cuotas cambio desde que se genero. */
    @Transactional
    public void validarLiquidacion(Long liquidacionId) {
        Map<Long, BigDecimal> porCuota = new TreeMap<>();
        for (LiquidacionItem it : liquidacionItemRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId)) {
            acumular(porCuota, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porCuota, "la liquidacion #" + liquidacionId);
    }

    /** Rechaza el pago del finiquito si alguna de sus cuotas cambio desde que se genero. */
    @Transactional
    public void validarFiniquito(Long liquidacionFinalId) {
        Map<Long, BigDecimal> porCuota = new TreeMap<>();
        for (LiquidacionFinalItem it : liquidacionFinalItemRepository.findByLiquidacionFinalIdOrderByIdAsc(liquidacionFinalId)) {
            acumular(porCuota, it.getReferenciaTipo(), it.getReferenciaId(), it.getMonto());
        }
        validar(porCuota, "el finiquito #" + liquidacionFinalId);
    }

    /**
     * Aplica el descuento al pagar: suma a la cuota y al prestamo. Se llama despues de validar, en la
     * misma transaccion; si la cuota ya no admite el monto (la misma cuota descontada por dos documentos
     * del mismo pago), lanza y el pago entero se deshace.
     */
    @Transactional
    public void aplicar(Long cuotaId, BigDecimal monto) {
        PrestamoCuota c = cuotaRepository.lockById(cuotaId).orElse(null);
        if (c == null) return;
        BigDecimal m = nz(monto);
        if (c.getEstado() == PrestamoCuotaEstado.PAGADA || c.getEstado() == PrestamoCuotaEstado.CANCELADA
                || pendiente(c).add(TOLERANCIA).compareTo(m) < 0) {
            throw new GraphQLException("La " + etiqueta(c) + " ya no admite un descuento de " + m.toPlainString()
                    + " (esta " + c.getEstado() + ", pendiente " + pendiente(c).toPlainString()
                    + "). Vuelva a borrador y regenere.");
        }
        c.setMontoPagado(nz(c.getMontoPagado()).add(m));
        actualizarEstado(c);
        cuotaRepository.save(c);

        Prestamo p = lockPrestamo(c);
        if (p == null) return;
        p.setMontoPagado(nz(p.getMontoPagado()).add(m));
        // Solo desde ACTIVO: un prestamo CANCELADO no vuelve a la vida por un descuento.
        if (p.getEstado() == PrestamoEstado.ACTIVO && p.getMontoPagado().compareTo(nz(p.getMontoTotal())) >= 0) {
            p.setEstado(PrestamoEstado.PAGADO);
        }
        prestamoRepository.save(p);
    }

    /**
     * Revierte el descuento al anular el pago: resta lo que puso el item (no lo que haya, que puede incluir
     * un cobro por caja posterior) y recalcula el estado en vez de volver siempre a PENDIENTE.
     */
    @Transactional
    public void revertir(Long cuotaId, BigDecimal monto) {
        PrestamoCuota c = cuotaRepository.lockById(cuotaId).orElse(null);
        if (c == null) return;
        BigDecimal pagadoAntes = nz(c.getMontoPagado());
        BigDecimal pagadoDespues = pagadoAntes.subtract(nz(monto)).max(BigDecimal.ZERO);
        BigDecimal restado = pagadoAntes.subtract(pagadoDespues);
        c.setMontoPagado(pagadoDespues);
        actualizarEstado(c);
        cuotaRepository.save(c);

        Prestamo p = lockPrestamo(c);
        if (p == null) return;
        p.setMontoPagado(nz(p.getMontoPagado()).subtract(restado).max(BigDecimal.ZERO));
        if (p.getEstado() == PrestamoEstado.PAGADO && p.getMontoPagado().compareTo(nz(p.getMontoTotal())) < 0) {
            p.setEstado(PrestamoEstado.ACTIVO);
        }
        prestamoRepository.save(p);
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private static void acumular(Map<Long, BigDecimal> porCuota, String tipo, Long refId, BigDecimal monto) {
        if (!REFERENCIA_CUOTA.equals(tipo) || refId == null) return;
        porCuota.merge(refId, nz(monto), BigDecimal::add);
    }

    /** Recorre las cuotas en orden de id (TreeMap): orden canonico de locks. */
    private void validar(Map<Long, BigDecimal> porCuota, String documento) {
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : porCuota.entrySet()) {
            PrestamoCuota c = cuotaRepository.lockById(e.getKey()).orElse(null);
            if (c == null) {
                problemas.add("la cuota #" + e.getKey() + " ya no existe");
            } else if (c.getEstado() == PrestamoCuotaEstado.PAGADA || c.getEstado() == PrestamoCuotaEstado.CANCELADA) {
                problemas.add(etiqueta(c) + " ya esta " + c.getEstado());
            } else if (pendiente(c).subtract(e.getValue()).abs().compareTo(TOLERANCIA) > 0) {
                problemas.add(etiqueta(c) + ": pendiente hoy " + pendiente(c).toPlainString()
                        + ", descontado " + e.getValue().toPlainString());
            }
        }
        if (!problemas.isEmpty()) {
            throw new GraphQLException("No se puede pagar " + documento
                    + ": cambiaron cuotas de prestamo desde que se genero (" + String.join("; ", problemas)
                    + "). Vuelva a borrador y regenere.");
        }
    }

    /** PAGADA si cubre; si no, VENCIDA si ya vencio (mismo criterio del scheduler), PARCIAL o PENDIENTE. */
    private static void actualizarEstado(PrestamoCuota c) {
        if (c.getEstado() == PrestamoCuotaEstado.CANCELADA) return;
        if (nz(c.getMontoPagado()).compareTo(nz(c.getMonto())) >= 0) {
            c.setEstado(PrestamoCuotaEstado.PAGADA);
            if (c.getFechaPago() == null) c.setFechaPago(LocalDate.now());
            return;
        }
        c.setFechaPago(null);
        if (c.getFechaVencimiento() != null && c.getFechaVencimiento().isBefore(LocalDate.now())) {
            c.setEstado(PrestamoCuotaEstado.VENCIDA);
        } else if (nz(c.getMontoPagado()).signum() > 0) {
            c.setEstado(PrestamoCuotaEstado.PARCIAL);
        } else {
            c.setEstado(PrestamoCuotaEstado.PENDIENTE);
        }
    }

    private Prestamo lockPrestamo(PrestamoCuota c) {
        if (c.getPrestamo() == null || c.getPrestamo().getId() == null) return null;
        return prestamoRepository.lockById(c.getPrestamo().getId()).orElse(null);
    }

    private static BigDecimal pendiente(PrestamoCuota c) {
        return nz(c.getMonto()).subtract(nz(c.getMontoPagado()));
    }

    private static String etiqueta(PrestamoCuota c) {
        return "cuota #" + c.getNumero() + " del prestamo #"
                + (c.getPrestamo() != null ? c.getPrestamo().getId() : "?");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
