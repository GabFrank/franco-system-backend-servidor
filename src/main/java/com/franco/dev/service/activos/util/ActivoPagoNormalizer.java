package com.franco.dev.service.activos.util;

import com.franco.dev.service.financiero.dto.EnteFinancialSummaryDTO;

import java.math.BigDecimal;

public final class ActivoPagoNormalizer {

    private ActivoPagoNormalizer() {
    }

    public static BigDecimal calcularMontoPendiente(BigDecimal montoTotal, BigDecimal montoYaPagado) {
        BigDecimal total = montoTotal != null ? montoTotal : BigDecimal.ZERO;
        BigDecimal pagado = montoYaPagado != null ? montoYaPagado : BigDecimal.ZERO;
        BigDecimal pendiente = total.subtract(pagado);
        return pendiente.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : pendiente;
    }

    /**
     * El saldo pendiente que se le informa al cliente, o {@code null} cuando el central no
     * tiene con que calcularlo.
     *
     * Sin monto total no hay deuda conocida. Devolver cero ahi afirma que el activo no debe
     * nada, que es una afirmacion distinta de "no hay datos cargados" — y la lee alguien que
     * esta por decidir cuanta plata pedir. Un activo recien dado de alta, sin financiacion,
     * caia justo en ese caso: la PWA mostraba "Pendiente: 0,00".
     *
     * {@link #calcularMontoPendiente} sigue devolviendo un numero siempre porque
     * {@link #debeMarcarComoPagado} lo compara; esta variante es la que va al DTO.
     */
    public static BigDecimal montoPendienteInformable(BigDecimal montoTotal, BigDecimal montoYaPagado) {
        if (montoTotal == null) {
            return null;
        }
        return calcularMontoPendiente(montoTotal, montoYaPagado);
    }

    /**
     * Un bien en estado PAGANDO sin saldo pendiente debe tratarse como pagado por completo,
     * incluyendo el ajuste de cuotas al 100% cuando existe un plan de cuotas.
     */
    public static boolean debeMarcarComoPagado(String situacionPago, BigDecimal montoTotal,
            BigDecimal montoYaPagado, Integer cuotasTotales) {
        String situacion = situacionPago != null ? situacionPago.toUpperCase() : "";
        if (!"PAGANDO".equals(situacion)) {
            return false;
        }
        if (calcularMontoPendiente(montoTotal, montoYaPagado).compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        BigDecimal total = montoTotal != null ? montoTotal : BigDecimal.ZERO;
        BigDecimal pagado = montoYaPagado != null ? montoYaPagado : BigDecimal.ZERO;
        int cuotas = cuotasTotales != null ? cuotasTotales : 0;
        return total.compareTo(BigDecimal.ZERO) > 0
                || pagado.compareTo(BigDecimal.ZERO) > 0
                || cuotas > 0;
    }

    public static void normalizarResumenSiPagadoCompleto(EnteFinancialSummaryDTO dto) {
        if (dto == null || !debeMarcarComoPagado(
                dto.getSituacionPago(), dto.getMontoTotal(), dto.getMontoYaPagado(), dto.getCuotasTotales())) {
            return;
        }
        dto.setSituacionPago("PAGADO");
        dto.setCuotasFaltantes(0);
        if (dto.getCuotasTotales() != null && dto.getCuotasTotales() > 0) {
            dto.setCuotasPagadas(dto.getCuotasTotales());
        }
        dto.setEstadoCuota("PAGADO");
    }
}
